# 중간 단계 승인은 version이 안 올라가서, 회수와 승인이 둘 다 성공하는 문제

- 단계: 6단계 (상신·전자결재) / 관련 PR: #6
- 키워드: 낙관적 락, `@Version`, `OPTIMISTIC_FORCE_INCREMENT`, 애그리거트 루트, `@Lock` + fetch join, `@Transactional` 테스트

## 상황

BR-06: "같은 문서를 거의 동시에 두 번 처리하면 한 번만 반영되고, 나중 요청은 '다른 사용자가 먼저 처리했습니다'를 보여 준다."

5단계에서 `PurchaseRequest`에 `@Version Long version`을 미리 넣어 두었기 때문에 "버전 필드가 있으니 동시 처리는 막힌다"고 생각했다.

## 증상

아래 순서를 테스트로 만들었다 (`ApprovalConcurrencyTest`).

```
트랜잭션 A (팀장 이팀장): 문서를 읽음 ───────────────────────────── 승인하고 커밋 → 성공 (!)
트랜잭션 B (기안자 김개발):          읽음 → 회수 → 커밋 → 성공
```

둘 다 예외 없이 커밋되었고, DB에는 이런 상태가 남았다.

| 테이블 | 값 |
|---|---|
| `purchase_request.status` | `DRAFT` (회수됨) |
| `approval_line` 1단계 | `APPROVED` (팀장이 승인) |
| `approval_line` 2단계 | `PENDING` (본부장 차례?) |

임시저장 문서인데 결재선은 진행 중이다. 본부장 결재함에 고칠 수 있는 상태의 문서가 올라온다.
같은 팀장이 두 탭에서 승인을 동시에 눌러도 둘 다 성공해서 의견이 마지막 것으로 덮어써졌다.

## 원인

`@Version`은 **그 엔티티의 행을 UPDATE할 때만** 검사하고 올린다.

```sql
update purchase_request set status=?, version=2 where id=? and version=1   -- 0건이면 충돌
```

중간 단계 승인에서 바뀌는 것은 `approval_line`(자식) 행뿐이다. 요청서 자신은 상태가 그대로(`IN_APPROVAL`)라
`purchase_request`에 UPDATE가 나가지 않고, 그래서 version 검사도 일어나지 않는다.

- 마지막 단계 승인·반려는 요청서 상태가 바뀌므로 version 검사가 일어난다. 그래서 **가끔만** 문제가 보인다.
- 컬렉션이 `mappedBy` 쪽(연관관계의 주인이 아닌 쪽)이라 자식이 바뀌어도 부모가 "변경됨"으로 보이지 않는다.

즉 "결재선까지 포함한 문서 전체"를 한 단위(애그리거트)로 생각했는데, DB의 낙관적 락은 행 단위로만 동작하고 있었다.

## 해결

### 1) 결재 처리할 때는 요청서 version을 무조건 올린다

```java
@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
@Query("select r from PurchaseRequest r where r.id = :id")
Optional<PurchaseRequest> findForApproval(@Param("id") Long id);
```

`OPTIMISTIC_FORCE_INCREMENT`는 엔티티가 안 바뀌어도 커밋 직전에 `version`을 +1 하는 UPDATE를 보낸다.
상신·회수·승인·반려가 모두 이 메서드로 문서를 읽으므로, 같은 문서를 처리하는 두 트랜잭션 중 나중에 커밋하는 쪽은
`where version = (읽을 때 값)` 이 0건이 되어 `ObjectOptimisticLockingFailureException`으로 실패한다.

결재선(자식)을 바꿀 때 부모(루트)의 version을 올리는 것: "애그리거트 루트로 동시성을 지킨다"는 말의 실제 모습이다.

### 2) 예외는 컨트롤러에서 잡는다

낙관적 락 예외는 서비스 메서드 안이 아니라 **커밋할 때**(서비스 메서드가 끝나고 프록시가 커밋하는 순간) 터진다.
그래서 서비스 안의 try-catch로는 못 잡는다. 트랜잭션 바깥인 `ApprovalController`에서 잡아
"다른 사용자가 먼저 처리했습니다" 메시지를 flash로 넘기고 상세 화면으로 돌려보낸다.

### 3) 같은 사람이 두 번 누른 경우의 메시지

순서대로 두 번 누르면(첫 번째 커밋이 끝난 뒤 두 번째) 락이 아니라 도메인 규칙에서 걸린다.
처음에는 "지금 결재할 차례가 아닙니다"가 나왔는데, 사용자는 방금 승인한 사람이라 어색하다.
이번 회차에 내가 이미 승인한 단계가 있으면 `ALREADY_PROCESSED`로 안내하도록 바꿨다.

## 해결하다 또 막힌 것: `@Lock` + `join fetch`

처음에는 기안자·부서도 쓰니까 한 번에 읽으려고 이렇게 썼다.

```java
@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
@Query("select r from PurchaseRequest r join fetch r.requester join fetch r.department where r.id = :id")
```

커밋할 때 터졌다.

```
JpaSystemException: Unable to perform beforeTransactionCompletion callback:
cannot force version increment on non-versioned entity
```

**락 모드는 fetch join으로 같이 읽은 엔티티에도 걸린다.** `Member`, `Department`에는 `@Version`이 없으니 올릴 version이 없다.
join fetch를 빼고 기안자·부서는 지연 로딩으로 읽게 했다 (쿼리 1~2번 더 나가지만, 결재 처리는 한 번에 한 건이라 괜찮다).

### 왜 기존 테스트는 통과했나

`ApprovalServiceTest`는 클래스에 `@Transactional`이 붙어 있어서 테스트가 끝나면 **롤백**한다.
`OPTIMISTIC_FORCE_INCREMENT`의 version 증가는 **커밋 직전**에 실행되므로, 롤백하는 테스트에서는 한 번도 실행되지 않았다.
`@Transactional` 없이 진짜로 커밋하는 `ApprovalConcurrencyTest`를 만들고 나서야 드러났다.

> 커밋 시점에 일어나는 일(낙관적 락, 지연 쓰기 제약 조건 위반, 커밋 후 콜백)은 롤백 테스트로는 확인할 수 없다.

## 전후 비교 (`ApprovalConcurrencyTest`)

| 시나리오 | 락 없이 읽기 | `OPTIMISTIC_FORCE_INCREMENT` |
|---|---|---|
| 회수 중에 팀장 승인 | 둘 다 성공. `DRAFT` + 결재선 `APPROVED/PENDING` (깨진 상태) | 승인 실패, 회수만 반영. 결재선 `CANCELED/CANCELED` |
| 같은 팀장이 두 탭에서 승인 | 둘 다 성공, 의견이 덮어써짐 | 두 번째 실패, 첫 번째 의견만 남음 |

### 테스트에서 "동시에"를 만든 방법

스레드 두 개와 `CountDownLatch`로 만들면 실행할 때마다 순서가 달라져서 가끔만 실패하는 테스트가 된다.
그래서 순서를 손으로 고정했다: 바깥 트랜잭션 A가 문서를 읽은 뒤, 그 안에서 `REQUIRES_NEW` 트랜잭션 B를 열어
회수를 끝까지 커밋하고, 다시 A로 돌아와 승인하고 커밋한다. 매번 같은 순서라 항상 같은 결과가 나온다.

## 배운 점

- `@Version`이 있다고 끝이 아니다. "무엇이 바뀌면 version이 올라가는가"를 확인한다. 자식만 바뀌는 변경은 부모 version을 안 올린다.
- 애그리거트 단위로 동시성을 지키려면 루트의 version을 강제로 올린다 (`OPTIMISTIC_FORCE_INCREMENT`).
- `@Lock`을 붙인 쿼리에는 fetch join을 함부로 붙이지 않는다. 락이 같이 읽은 엔티티에도 걸린다.
- 커밋할 때 일어나는 일은 `@Transactional`(롤백) 테스트로 확인할 수 없다. 커밋하는 테스트를 따로 둔다.
- 참고: 요청서가 바뀌는 처리(상신·회수·마지막 승인)는 일반 version 증가 + 강제 증가로 version이 2씩 오른다. 숫자 자체에는 의미가 없고 "바뀌었는지"만 본다.
