# 트랜잭션 안에서 외부 API를 부르면 커넥션 풀이 바닥나는 문제

- 단계: 8단계 (국세청 사업자 상태 조회) / 관련 PR: #8
- 키워드: `@Transactional` 범위, HikariCP 커넥션 풀, 외부 API 타임아웃, RestClient, 서비스 키 인코딩

## 상황

거래처 [상태 확인]을 누르면 국세청 API로 계속/휴업/폐업을 확인해 저장한다 (BR-09).
처음 떠오르는 코드는 "거래처 읽기 → API 부르기 → 저장"을 서비스 메서드 하나에 넣고 `@Transactional`을 붙이는 것이다.

```java
@Transactional
public void refresh(Long id) {
    Vendor v = vendorRepository.findById(id).orElseThrow();
    Result r = ntsClient.fetchStatuses(List.of(v.getBizNo()));   // ← 외부 HTTP, 느릴 수 있음
    v.changeStatus(r.status(), now());
}
```

## 증상 (`ExternalCallConnectionPoolTest`)

커넥션 풀 2개, 커넥션 대기 0.5초로 줄이고 국세청 응답을 일부러 늦췄다. 두 명이 동시에 [상태 확인]을 누른 상태에서 세 번째 사람이 아무 화면이나 열면:

```
CannotCreateTransactionException: Could not open JPA EntityManager for transaction
Caused by: SQLTransientConnectionException:
  HikariPool-1 - Connection is not available, request timed out after 500ms (total=2, active=2, idle=0, waiting=0)
```

`active=2`: 두 커넥션 모두 "사용 중"인데 실제로는 DB에 아무 일도 안 하고 국세청 응답만 기다리고 있다.
실서버(풀 10개, 기본 대기 30초)라면 10명이 동시에 누른 순간 모든 화면이 30초씩 멈췄다가 오류가 난다. 외부 API 하나가 느린 것이 시스템 전체 장애가 된다.

## 원인

`@Transactional`은 메서드가 **시작할 때 DB 커넥션을 빌리고 끝날 때 돌려준다**. 그 사이에 무엇을 하든 커넥션은 계속 빌린 상태다.
외부 API 대기 시간(최악이면 타임아웃까지)이 그대로 커넥션 점유 시간이 된다.

## 해결

### 1) 트랜잭션을 나누고, API는 그 사이(트랜잭션 밖)에서 부른다

```java
// VendorStatusService: 클래스에 @Transactional 없음
public CheckResult refresh(Long vendorId) {
    Vendor vendor = vendorService.get(vendorId);                  // 짧은 읽기 트랜잭션 → 커넥션 반납
    var r = ntsClient.fetchStatuses(List.of(vendor.getBizNo()));  // 트랜잭션 밖: 커넥션 안 잡음
    vendorService.applyStatus(vendorId, r.status(), now());       // 짧은 쓰기 트랜잭션
}
```

같은 테스트에서 두 명이 API를 기다리는 동안에도 세 번째 조회가 0.5초 안에 바로 성공한다.

트레이드오프: 읽기와 쓰기 사이에 다른 사람이 거래처를 고칠 수 있다. 여기서는 "상태 + 확인 시각"만 덮어쓰므로 괜찮다고 판단했다. 꼭 막아야 한다면 쓰기 단계에서 version을 비교한다.

### 2) 외부 API에는 반드시 타임아웃

연결 2초, 응답 3초 (`app.nts.connect-timeout`, `read-timeout`). 타임아웃이 없으면 상대 서버가 응답을 안 줄 때 요청 스레드가 무한정 기다린다.
`NtsBusinessClientTest`에서 "연결은 받지만 응답을 안 주는 서버"를 만들어 0.3초 만에 끊기는 것을 확인했다.

### 3) 실패하면 예외 대신 "확인 안 됨"

API가 실패해도 화면이 오류로 깨지지 않게, 클라이언트는 `NtsApiException` 하나로 바꿔 던지고 서비스는 `UNKNOWN`으로 저장한 뒤 이유를 안내한다 (BR-09).
키가 아예 없을 때는 API 실패가 아니라 설정 문제라서, 부르지도 저장하지도 않는다 (담당자가 정한 상태를 덮어쓰지 않음).

## 해결하다 또 막힌 것

1. **타임아웃 예외 타입.** `ResourceAccessException`이 올 줄 알고 그것만 잡았는데, 응답 본문을 읽다가 끊기면 `RestClientException`(원인 `SocketTimeoutException`)으로 왔다. 그래서 상위 타입 `RestClientException`을 잡는다. 테스트가 아니었으면 운영에서 500 오류로 처음 봤을 것이다.
2. **서비스 키의 `+`.** 공공데이터포털 키에는 `+`, `/`, `=`가 들어 있다. `queryParam("serviceKey", key)`로 값을 바로 넣으면 `+`가 인코딩되지 않고 그대로 나가서, 서버가 공백으로 읽는다 → "등록되지 않은 키". `queryParam("serviceKey", "{serviceKey}")`처럼 변수로 넘겨야 `%2B`로 인코딩된다. 포털의 "Encoding 키"(이미 `%2B`로 바뀐 것)를 넣으면 반대로 두 번 인코딩된다 → 반드시 "Decoding 키"를 쓴다. `NtsBusinessClientTest`가 실제로 나가는 주소(`serviceKey=abc%2Bdef%2Fghi%3D%3D`)를 검사한다.
3. **내 맥의 `.env`에 진짜 키를 넣으면 테스트가 진짜 국세청을 부를 수 있다.** `application-test.yml`에서 키를 빈 값으로, 주소를 `localhost:1`로 덮어썼다.

## 전후 비교

| 상황 (풀 2개, 대기 0.5초) | 트랜잭션 안에서 호출 | 트랜잭션 밖에서 호출 |
|---|---|---|
| 2명이 느린 API를 기다리는 중 3번째 조회 | 0.5초 뒤 `Connection is not available` (active=2) | 바로 성공 (0.5초 미만) |

## 배운 점

- `@Transactional` 범위 = DB 커넥션을 잡고 있는 시간. 그 안에는 DB 일만 둔다. 외부 HTTP, 파일 업로드, 메일 발송은 밖으로.
- 외부 호출에는 타임아웃 + 실패했을 때의 기본값(여기서는 "확인 안 됨")을 같이 설계한다.
- 예외 타입은 추측하지 말고 실패하는 상황을 테스트로 만들어 확인한다.
