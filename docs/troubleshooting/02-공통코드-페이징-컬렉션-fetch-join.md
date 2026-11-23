# 공통코드 목록: 컬렉션 fetch join에 페이징을 붙였더니 전체를 메모리로 읽는 문제

- 단계: 4단계 / 관련 PR: #4
- 키워드: JPA, 페이징, 컬렉션 fetch join, HHH90003004, default_batch_fetch_size, N+1

## 상황

공통코드 관리 화면(`/admin/codes`)은 코드 그룹을 **10개씩 페이지로** 보여 주고, 한 줄마다 그 그룹의 코드 이름(`개, 박스, 세트…`)을 같이 보여 줍니다.
그룹(`CodeGroup`) 1개에 코드(`CommonCode`) 여러 개가 달린 **1:N(@OneToMany)** 구조입니다.

3단계에서 N+1을 fetch join으로 해결했으니, 이번에도 그대로 fetch join을 붙였습니다.

```java
queryFactory.selectFrom(codeGroup)
        .leftJoin(codeGroup.codes, commonCode).fetchJoin()   // 코드까지 한 번에
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())                       // 10개만
        .fetch();
```

## 증상

화면은 제대로 10개가 나왔는데, 로그에 경고가 찍혔습니다.

```
WARN org.hibernate.orm.query : HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
```

실행된 SQL을 보니 **`limit`이 없습니다.**

```sql
select cg1_0.group_code, c1_0.id, c1_0.code, ...
from code_group cg1_0
left join common_code c1_0 on cg1_0.group_code = c1_0.group_code
order by cg1_0.group_code, c1_0.sort_order, c1_0.code
-- limit ? 가 없다!
```

테스트(`CodeGroupPagingProblemTest`, 그룹 30개 × 코드 5개)로 세어 보니:

```
[컬렉션 fetch join + 페이징] 화면에 쓸 그룹 10개를 위해 DB에서 읽은 엔티티 수 = 180
```

그룹 10개만 필요한데 **그룹 30개 + 코드 150개 = 180개를 전부** 읽고 자바 메모리에서 10개로 잘랐습니다.
지금은 180개지만 코드가 수만 건이면 페이지 하나 열 때마다 전부 메모리로 올라와 `OutOfMemoryError`까지 갈 수 있습니다.

## 원인

1:N을 join하면 **행이 "자식 수만큼" 늘어납니다.**

```
code_group  common_code
GROUP_01    C1
GROUP_01    C2      ← 그룹 1개가 5행이 된다
GROUP_01    C3
GROUP_01    C4
GROUP_01    C5
GROUP_02    C1
...
```

여기에 SQL `limit 10`을 걸면 "그룹 10개"가 아니라 "행 10개"(= 그룹 2개)가 잘립니다. 그래서 Hibernate는 SQL로 자르는 걸 포기하고
**전부 가져온 뒤 메모리에서 그룹 기준으로 10개를 자르고**, 대신 경고만 남깁니다. 화면은 멀쩡해 보여서 놓치기 쉽습니다.

> 3단계의 사용자 → 부서는 **N:1(@ManyToOne)** 이라 join해도 행이 늘지 않습니다. 그래서 그때의 fetch join은 페이징과 같이 써도 괜찮습니다 (`MemberRepositoryTest.paging`: 쿼리 2번).

그렇다고 fetch join을 그냥 빼면 3단계의 N+1이 다시 생깁니다.

```
[배치 없음] 그룹 10개 페이지 쿼리 수 = 12      (내용 1 + 건수 1 + 그룹마다 코드 1번씩 10)
```

## 해결

두 가지를 같이 했습니다.

**1) 컬렉션은 fetch join 하지 않고, `default_batch_fetch_size`로 묶어서 읽는다**

```yaml
# application.yml
spring.jpa.properties.hibernate.default_batch_fetch_size: 100
```

```java
// CodeGroupRepositoryImpl: 그룹만 limit으로 10개
queryFactory.selectFrom(codeGroup)
        .where(keywordContains(keyword))
        .orderBy(codeGroup.groupCode.asc())
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

// CodeGroupRow.from(): 첫 번째 그룹의 codes를 읽는 순간, 화면에 있는 그룹 10개의 코드를 한 번에 가져온다
group.getCodes().stream().map(CommonCode::getName).toList();
```

```sql
select ... from common_code c1_0
where c1_0.group_code in (?, ?, ?, ... )   -- 그룹 10개의 코드를 IN 한 번으로
order by c1_0.sort_order, c1_0.code
```

> IN 절의 `?`가 실제로는 100개 찍힙니다. Hibernate 6은 SQL 모양을 매번 같게(=DB가 실행 계획을 재사용하게) 하려고 남는 자리를 채워서 보냅니다. 값은 10개만 들어갑니다.

**2) 누가 다시 컬렉션 fetch join + 페이징을 쓰면 경고가 아니라 오류가 나게 막는다**

```yaml
spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch: true
```

```
HibernateException: setFirstResult() or setMaxResults() specified with collection fetch join
(in-memory pagination was about to be applied, but 'hibernate.query.fail_on_pagination_over_collection_fetch' is enabled)
```

경고는 로그에 묻히지만 오류는 테스트와 개발 중에 바로 보입니다. (`CodeGroupPagingTest.collectionFetchJoinWithPagingFails`)

## 전후 비교

그룹 30개 × 코드 5개, 한 페이지 10개 (H2, `Statistics`로 측정)

| 방법 | SQL 수 | DB에서 읽은 엔티티 | 문제 |
|---|---|---|---|
| 컬렉션 fetch join + 페이징 | 1 | **180** | limit 없음, 메모리에서 자름 |
| fetch join 없이 지연 로딩 | **12** | 60 | N+1 |
| 지연 로딩 + `default_batch_fetch_size: 100` | **3** | **60** | 없음 |

3단계 사용자 목록과 묶어 보면:

| 관계 | 페이징 없음 | 페이징 있음 |
|---|---|---|
| N:1 (사용자 → 부서) | fetch join | fetch join (안전) |
| 1:N (그룹 → 코드) | fetch join 가능 (상세 화면 `findWithCodes`) | **batch fetch size** |

## 배운 점

- 로그의 `HHH90003004`는 "지금 전부 메모리로 읽고 있다"는 뜻. 화면이 멀쩡해도 무시하면 안 된다.
- **1:N에 fetch join + 페이징은 쓰지 않는다.** 부모만 페이징하고, 자식은 batch fetch size로 IN 한 번.
- `default_batch_fetch_size`는 전역 설정이라 3단계처럼 순수 N+1을 재현하려면 테스트에서 따로 꺼야 했다 (`MemberRepositoryTest`의 `@DataJpaTest(properties = ...)`).
- 경고를 오류로 바꾸는 설정(`fail_on_pagination_over_collection_fetch`)을 켜 두면 같은 실수가 다시 들어오지 않는다.
