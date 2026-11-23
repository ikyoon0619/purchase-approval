# 트러블슈팅 기록

개발하면서 실제로 막혔던 문제를 한 파일에 하나씩 남깁니다. 각 파일은 아래 틀을 따릅니다.

```markdown
# 제목 (한 줄로 무슨 문제였는지)

- 단계: N단계 / 관련 PR: #
- 키워드: JPA, N+1, ...

## 상황
무엇을 하려고 했는지

## 증상
에러 메시지, 로그, 화면 (그대로 붙여 넣기)

## 원인
왜 그런 일이 생겼는지

## 해결
무엇을 바꿨는지 (코드 조각)

## 전후 비교
쿼리 수, 실행 시간, 테스트 결과 같은 숫자

## 배운 점
다음에 같은 상황이면 무엇을 먼저 볼지
```

## 목록

| # | 제목 | 단계 | 키워드 |
|---|---|---|---|
| 01 | [사용자 목록을 열 때 쿼리가 11번 나가는 문제 (N+1)](01-사용자-목록-N+1.md) | 3단계 | JPA, N+1, fetch join |
| 02 | [공통코드 목록: 컬렉션 fetch join + 페이징이 전체를 메모리로 읽는 문제](02-공통코드-페이징-컬렉션-fetch-join.md) | 4단계 | 페이징, 컬렉션 fetch join, batch fetch size |
| 03 | [open-in-view를 껐더니 부서 검색 화면만 500 (LazyInitializationException)](03-open-in-view-끄고-LazyInitializationException.md) | 5단계 | OSIV, 프록시, 1차 캐시, DTO |
| 04 | [중간 단계 승인은 version이 안 올라가서, 회수와 승인이 둘 다 성공하는 문제](04-중간-결재는-version이-안-올라가는-문제.md) | 6단계 | 낙관적 락, OPTIMISTIC_FORCE_INCREMENT, @Lock + fetch join |
| 05 | [같은 품목을 동시에 입고하면 재고 수량이 어긋나는 문제](05-동시-입고-재고-유실.md) | 7단계 | Lost Update, 비관적 락, FOR UPDATE, 데드락 |
| 06 | [트랜잭션 안에서 외부 API를 부르면 커넥션 풀이 바닥나는 문제](06-트랜잭션-안-외부-API-커넥션-풀.md) | 8단계 | @Transactional 범위, HikariCP, 타임아웃, 서비스 키 인코딩 |
| 07 | [같은 달 통계를 다시 집계하면: "이미 완료" 거절, 그다음엔 UNIQUE 위반](07-배치-재실행-멱등성.md) | 9단계 | Spring Batch, JobParameters, 멱등성, upsert, tasklet 트랜잭션 |
