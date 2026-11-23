# 구매요청·전자결재 시스템 (purchase-approval)

직원이 구매요청서를 쓰면 팀장 → 부서장 → (500만 원 이상이면) 구매팀장 순서로 결재하고,
승인되면 발주와 재고에 반영되는 사내 업무 시스템입니다.

단계별로 Pull Request를 하나씩 올리며 만들었습니다 (#1 ~ #11). 만들면서 실제로 막힌 문제는 [트러블슈팅](#트러블슈팅-요약)에 남겼습니다.

## 기술 스택

Java 17 · Spring Boot 3.3 · Spring Data JPA · QueryDSL · Thymeleaf · Spring Security · Apache POI · MySQL 8 · Spring Batch · JUnit 5 · Docker · GitHub Actions · AWS EC2

## 실행 방법

필요한 것: JDK 17 이상, Docker Desktop

```bash
cp .env.example .env              # DB 비밀번호 등 (처음 한 번)
docker compose up -d              # MySQL 실행
./gradlew bootRun                 # http://localhost:8080
```

- 처음 뜰 때 샘플 부서 6개, 직급 5개, 사용자 40명(페이징 확인용 직원 30명 포함), 메뉴, 공통코드 그룹 12개, 품목 12개, 거래처 6곳(휴업·폐업 포함), dev1의 요청서 3건이 자동으로 들어갑니다.
- 첨부파일은 `./uploads`에 저장됩니다 (`UPLOAD_DIR` 환경변수로 변경).
- 샘플 계정 (비밀번호는 모두 `pass1234`)

| 아이디 | 이름 | 역할 | 확인할 수 있는 것 |
|---|---|---|---|
| `admin` | 관리자 (인사팀) | 관리자 | 사용자·부서·직급·공통코드·메뉴 권한 관리, 사용자 엑셀 다운로드 |
| `dev1`, `dev2` | 김개발, 오코드 (개발팀) | 직원 | 구매요청서 작성(dev1은 샘플 3건), 관리 메뉴는 403 |
| `devlead` | 이팀장 (개발팀) | 팀장 | |
| `bizhead` | 박본부 (사업본부) | 부서장 | |
| `buylead` | 강구매 (구매팀) | 구매팀 | 품목·거래처 관리, 모든 요청서 보기 |
- 끄기: `Ctrl+C`, MySQL까지 끄려면 `docker compose down` (데이터는 남음)

테스트 (Docker 없이 메모리 H2로 실행, 118개):

```bash
./gradlew test
```

## 배포 (AWS EC2 + Docker)

- `Dockerfile`: 멀티 스테이지 (JDK로 빌드 → JRE 이미지에 jar만), root가 아닌 사용자로 실행, 한국 시간.
- `docker-compose.prod.yml`: MySQL 8 + 앱. MySQL은 포트를 열지 않고, 앱은 MySQL이 준비된 뒤(healthcheck) 뜹니다.
- `prod` 프로필: DB 주소·비밀번호·키는 모두 환경변수(`.env`), 테이블은 **Flyway**(`db/migration`)가 만들고 JPA는 `ddl-auto: validate`로 검사만. 샘플 데이터는 기본으로 끄고, 사용자가 0명이면 `ADMIN_INITIAL_PASSWORD`로 admin 하나만 만듭니다.
- GitHub Actions
  - CI: 테스트 + **docker-smoke**(이미지 빌드 → MySQL 8과 함께 띄움 → Flyway·validate → 로그인·관리자 화면 확인 → 재시작 후 다시 확인)
  - Deploy: main 머지 → 테스트 → `ghcr.io`에 이미지 → (설정 시) SSH로 EC2에서 `deploy/deploy.sh`
- 순서대로 따라 하는 가이드: [docs/deploy/EC2-배포.md](docs/deploy/EC2-배포.md)

## 로그인과 권한

- Spring Security 세션 로그인 (폼 로그인, CSRF 켬, 비밀번호는 BCrypt로 저장)
- 메뉴마다 볼 수 있는 역할을 DB에 저장하고(`menu_role`), 요청 주소로 메뉴를 찾아 권한을 검사합니다 (`MenuAuthorizationManager`).
  관리자가 화면에서 체크만 바꾸면 메뉴 노출과 URL 접근이 함께 바뀝니다.
- `/admin/**`는 메뉴 설정과 관계없이 관리자만 들어갈 수 있습니다.

## 전자결재

- 상신하면 결재선을 조직도에서 자동으로 만듭니다: 팀장 → 본부장 → (500만 원 이상) 구매팀장. 기안자 본인 단계는 건너뜁니다.
- 상세 화면에서 상신 전에 결재선을 미리 보여 주고, 결재자를 못 찾으면 이유를 보여 줍니다.
- 결재함: 결재 대기 / 내가 처리한 문서 / 진행 중 문서. 왼쪽 메뉴에 대기 건수가 붙습니다.
- 회수는 첫 결재 전에만, 반려는 의견 필수, 반려된 문서는 "복사해서 다시 작성".
- 같은 문서를 동시에 처리하면 한 건만 반영됩니다 (트러블슈팅 04).

## 거래처 사업자 상태 (국세청 API)

- 공공데이터포털 "국세청 사업자등록정보 상태조회" API로 계속/휴업/폐업을 확인합니다. 거래처 등록 시 자동, 목록에서 한 곳 또는 전체(100곳씩 나눠 호출).
- 키는 `.env`의 `NTS_SERVICE_KEY`("Decoding" 키). 없으면 호출하지 않고 안내만 합니다. API가 실패하면 "확인 안 됨"으로 저장합니다.
- API 호출은 트랜잭션 밖에서, 연결 2초·응답 3초 타임아웃 (트러블슈팅 06).

## 발주·재고

- 구매팀이 최종 승인된 요청서로 발주서(`PO-yyyyMM-0001`)를 만들고, 물건이 오면 줄마다 실제 입고 수량을 적어 입고 처리합니다.
- 입고하면 품목별 재고가 늘고 이력(처리 후 수량, 관련 발주, 처리자)이 남습니다. 출고는 재고보다 많이 할 수 없습니다.
- 재고 행은 `SELECT … FOR UPDATE`로 잠근 뒤 바꿉니다. 여러 품목은 품목 id 순서로 잠가 데드락을 피합니다 (트러블슈팅 05).
- 샘플: `dev1` 상신 → `devlead` 승인 → `bizhead` 승인. 660만 원 문서는 `buylead`까지. 반려 예시는 `sales1`.

## 통계·대시보드·알림 (Spring Batch)

- 매일 02:00 `nightlyJob`: ① 어제가 속한 달의 부서별 승인 건수·금액 집계 → ② 3일 넘게 결재를 기다린 결재자에게 알림 → ③ 거래처 사업자 상태 갱신(국세청 API).
- 첫 화면(대시보드): 내 결재 대기·결재중·임시저장, 이번 달 우리 부서 승인 금액, 최근 6개월 막대, 부서별 막대, 최근 알림.
- 구매 통계(`/stats/monthly`, 부서장·구매팀·관리자): 부서 × 월 표. 구매팀·관리자는 한 달을 골라 [다시 집계].
- 다시 집계해도 결과가 같습니다(upsert). 야간 배치는 하루 한 번만 돕니다(`runDate` 파라미터) (트러블슈팅 07).
- local에서는 시작할 때 통계가 비어 있으면 최근 6개월을 한 번 집계합니다. 샘플: `devlead` 로그인 → 5일째 대기 문서 알림.

## 감사 로그·에러 로그 (AOP)

- 서비스 메서드에 `@Audited(action, target, id)` 한 줄을 붙이면 `AuditAspect`가 누가·언제·무엇을·어느 대상에·성공/실패·IP를 남깁니다 (요청서, 결재, 발주·입고·출고, 기준정보, 사용자·부서, 메뉴 권한, 수동 집계).
- 실패한 시도도 남고(REQUIRES_NEW), "성공"은 커밋이 확정된 뒤에 씁니다 (트러블슈팅 08).
- 처리하지 못한 예외는 `error_log`에 저장하고 사용자에게 오류 번호를 보여 줍니다. 야간 배치 실패도 저장합니다.
- 관리자 메뉴: 감사 로그(아이디·행위·대상·성공 여부·기간 검색), 에러 로그(목록, 스택 트레이스 상세).

## 트러블슈팅 (요약)

| # | 문제 | 해결 | 결과 |
|---|---|---|---|
| 01 | [사용자 목록 N+1](docs/troubleshooting/01-사용자-목록-N+1.md) | QueryDSL fetch join | 쿼리 11번 → 1번 |
| 02 | [컬렉션 fetch join + 페이징](docs/troubleshooting/02-공통코드-페이징-컬렉션-fetch-join.md) | batch fetch size + 오류로 막기 | 읽은 엔티티 180개 → 60개, 쿼리 12번 → 3번 |
| 03 | [OSIV 끄고 LazyInitializationException](docs/troubleshooting/03-open-in-view-끄고-LazyInitializationException.md) | fetch join + 화면에는 DTO | 검색할 때만 나던 500 → 200 |
| 04 | [중간 결재는 version이 안 올라가는 문제](docs/troubleshooting/04-중간-결재는-version이-안-올라가는-문제.md) | 루트에 `OPTIMISTIC_FORCE_INCREMENT` | 회수·승인 동시 처리: 둘 다 성공(깨진 상태) → 하나만 성공 |
| 05 | [동시 입고 재고 유실](docs/troubleshooting/05-동시-입고-재고-유실.md) | `SELECT … FOR UPDATE` + 잠금 순서 고정 | 동시 입고 5+3: 3개(유실) → 10명 동시 +1: 항상 10개 |
| 06 | [트랜잭션 안 외부 API 호출](docs/troubleshooting/06-트랜잭션-안-외부-API-커넥션-풀.md) | 읽기·쓰기 트랜잭션을 나누고 API는 그 밖에서 + 타임아웃 | 풀 2개에서 느린 API 2건 대기 중 다른 조회: 실패 → 즉시 성공 |
| 07 | [배치 재실행: 이미 완료 거절, UNIQUE 위반](docs/troubleshooting/07-배치-재실행-멱등성.md) | 파라미터 설계(runDate / requestedAt) + upsert writer + dedupKey | 같은 달 다시 집계: 거절·FAILED → 몇 번을 돌려도 줄 1개, 같은 값 |
| 08 | [감사 로그: 롤백과 커밋 실패](docs/troubleshooting/08-감사-로그-롤백과-커밋-실패.md) | REQUIRES_NEW 저장 + 트랜잭션 바깥 aspect + afterCompletion | 실패 시도 기록 0건 → 1건, 커밋 충돌 승인: 거짓 성공 → 실패로 기록 |
| 09 | [운영 스키마: Flyway + validate와 enum 컬럼](docs/troubleshooting/09-운영-스키마-Flyway-enum-컬럼.md) | enum 필드를 `columnDefinition = "varchar"`로, 마이그레이션 불변, CI에서 MySQL+Docker로 띄우기 | enum 상수 추가 시 validate 통과 후 `Data truncated` → 코드만 배포, 마이그레이션 누락은 CI에서 발견 |

## 프로젝트 구조

```
src/main/java/com/example/purchase
├── common/      BaseEntity(생성·수정 시각·작성자), 업무 예외, 공통 예외 처리, 엑셀 쓰기(POI), 페이지 링크, 첨부 저장
├── config/      JPA Auditing, QueryDSL, Spring Security, 샘플 데이터(app.sample-data), 첫 관리자 계정
├── security/    로그인 사용자(UserDetails), 메뉴 기반 URL 권한 검사
├── menu/        메뉴와 역할별 권한
├── org/         사용자·부서·직급 (조직도), 사용자 엑셀 다운로드
├── master/      품목, 거래처 (기준정보)
├── request/     구매요청서 작성·임시저장·수정·삭제, 첨부파일, 내 요청 목록, 결재선(ApprovalLine)
├── approval/    결재선 자동 생성, 상신·회수·승인·반려, 결재함
├── order/       발주서, 입고 처리
├── inventory/   품목별 재고, 재고 이력, 출고 (비관적 락)
├── stats/       월별 구매 통계, Spring Batch job(nightlyJob, monthlyStatJob), 스케줄러
├── notification/ 알림, 결재 지연 알림
├── audit/       @Audited, AuditAspect, 감사 로그·에러 로그 관리자 화면
├── errorlog/    처리 못 한 예외 저장
├── code/        공통코드 그룹·코드 (품목 분류, 단위 등)
└── home/        대시보드
src/main/resources/templates
├── layout/      공통 레이아웃 (Thymeleaf Layout Dialect)
├── fragments/   헤더, 왼쪽 메뉴 (역할에 따라 다르게 보임)
├── admin/       사용자·부서·직급·메뉴 권한 관리
└── error/       오류 화면
src/main/resources/db/migration   Flyway SQL (운영 테이블)
deploy/          deploy.sh(EC2에서 배포), smoke-test.sh(배포 후 로그인 확인)
Dockerfile, docker-compose.yml(로컬 MySQL), docker-compose.prod.yml(운영: MySQL + 앱)
```

## 문서

- [요구사항 정의서](docs/01-요구사항-정의서.md)
- [ERD](docs/02-ERD.md)
- [화면 목록](docs/03-화면-목록.md)
- [결재 상태도](docs/04-결재-상태도.md)
- [트러블슈팅 기록](docs/troubleshooting/README.md)
- [EC2 배포 가이드](docs/deploy/EC2-배포.md)
