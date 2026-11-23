# open-in-view를 껐더니 부서 검색 화면만 500 오류가 나는 문제 (LazyInitializationException)

- 단계: 5단계 / 관련 PR: #5
- 키워드: OSIV(open-in-view), LazyInitializationException, 프록시, 1차 캐시, fetch join, DTO

## 상황

5단계에서 구매요청서처럼 연관관계가 많은 화면을 만들기 전에 `spring.jpa.open-in-view: false`로 바꿨습니다.

- 기본값(`true`)은 요청이 들어올 때부터 **화면을 다 그릴 때까지** DB 커넥션을 붙잡고 있어서, 화면(Thymeleaf)에서도 지연 로딩이 됩니다.
- 편하지만 화면이 느리거나 요청이 몰리면 커넥션 풀이 바닥나고, "화면에서 몰래 쿼리가 나가는" 코드가 생기기 쉽습니다.

끄고 나서 기존 화면을 하나씩 열어 봤습니다.

```
/                      200
/admin/members         200
/admin/departments     200
/admin/positions       200
/admin/codes           200
```

다 괜찮아 보였습니다.

## 증상

그런데 **부서 관리에서 "개발"로 검색**하니 500 오류가 났습니다.

```
/admin/departments?keyword=개발   500
```

```
TemplateProcessingException: Exception evaluating SpringEL expression:
  "d.parent != null ? d.parent.name : '-'" (template: "admin/departments/list" - line 24, col 21)
Caused by: org.hibernate.LazyInitializationException:
  could not initialize proxy [com.example.purchase.org.domain.Department#2] - no Session
```

검색 없이 열면 되고, 검색하면 안 되는 이상한 상황이었습니다.

## 원인

부서 목록 화면은 한 줄마다 상위 부서 이름(`d.parent.name`)을 보여 줍니다. `parent`는 `@ManyToOne(fetch = LAZY)`라서
부서를 읽을 때 상위 부서 자리에는 **프록시(가짜 객체)**가 들어갑니다. 단, 예외가 하나 있습니다.

> 같은 영속성 컨텍스트(1차 캐시)에 그 엔티티가 **이미 들어 있으면** 프록시 대신 진짜 객체를 넣어 준다.

| 경우 | 조회 결과 | 상위 부서(본부) | 화면에서 `d.parent.name` |
|---|---|---|---|
| 검색 없음 | 본부 2 + 팀 4 = 6개 | 같은 결과 안에 본부가 있어서 **진짜 객체** | 성공 (DB 안 감) |
| "개발" 검색 | 개발팀 1개 | 본부는 결과에 없음 → **프록시** | 화면을 그릴 땐 세션이 닫혀 있음 → **LazyInitializationException** |

OSIV를 켜 둔 동안에는 화면에서 프록시를 초기화할 수 있었기 때문에 이 차이가 드러나지 않았습니다.
**"데이터에 따라 될 때도 있고 안 될 때도 있는"** 오류라서, 끄자마자 모든 화면을 확인해도 놓칠 수 있었습니다.

## 해결

**1) 화면에서 쓰는 연관 엔티티는 조회할 때 같이 가져온다 (fetch join)**

```java
// DepartmentRepositoryImpl.search()
QDepartment parent = new QDepartment("parent");   // 같은 테이블을 한 번 더 join → 별칭을 따로
return queryFactory.selectFrom(department)
        .leftJoin(department.parent, parent).fetchJoin()   // 본부는 parent가 null이라 left join
        .where(nameContains(keyword), usedEq(used))
        .orderBy(department.code.asc())
        .fetch();
```

**2) 새 화면(구매요청서)은 처음부터 엔티티 대신 DTO를 화면에 넘긴다**

```java
// RequestService (트랜잭션 안)
public RequestDetail getDetail(Long id, LoginUser user) {
    PurchaseRequest request = findDetail(id);   // 기안자·부서·거래처 fetch join
    checkCanView(request, user);
    return toDetail(request, user);             // 품목 줄·품목·첨부를 여기서 다 읽어 record에 담음 (batch fetch)
}
```

화면(`requests/detail.html`)은 `RequestDetail` 값만 읽으므로 DB를 건드리지 않습니다.

## 전후 비교

| | 전 | 후 |
|---|---|---|
| `/admin/departments?keyword=개발` | 500 (LazyInitializationException) | 200, "사업본부" 표시 |
| 부서 목록 쿼리 | 부서 1번 (+ OSIV일 때 화면에서 상위 부서 추가 조회) | 부서+상위 부서 1번 |
| 화면에서 나가는 쿼리 | 있을 수 있음 | 없음 (세션이 이미 닫혀 있어서 구조적으로 불가) |

테스트로 고정 (`LazyInitializationTest`, 클래스에 `@Transactional`을 일부러 안 붙임):

- `entityOutsideTransaction`: 트랜잭션 밖에서 `request.getItems().size()` → `failed to lazily initialize a collection ... - no Session`, `request.getVendor().getName()` → `could not initialize proxy ... - no Session`
- `dtoOutsideTransaction`: 서비스가 돌려준 `RequestDetail`은 밖에서 다 읽힘
- `departmentSearchWithoutParentInResult`: 실제로 깨졌던 "개발" 검색이 200이고 "사업본부"가 보임

## 배운 점

- OSIV를 끄는 건 설정 한 줄이지만, **화면에서 지연 로딩하던 곳을 전부 찾아야** 하는 작업이다.
- 1차 캐시 때문에 "같은 화면이 데이터에 따라 되기도 하고 안 되기도" 한다. 검색 조건을 바꿔 가며 확인해야 한다.
- 테스트 클래스에 `@Transactional`을 붙이면 테스트 전체가 한 트랜잭션이라 이 문제가 **절대 재현되지 않는다**. 재현하려면 트랜잭션 없이 돌려야 한다.
- 원칙: 화면에 필요한 데이터는 서비스(트랜잭션) 안에서 다 읽고, 화면에는 DTO를 넘긴다. N:1은 fetch join, 1:N은 batch fetch (02번 문서).
