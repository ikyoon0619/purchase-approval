# 구매요청·전자결재 시스템 (purchase-approval)

직원이 구매요청서를 쓰면 팀장 → 부서장 → (500만 원 이상이면) 구매팀장 순서로 결재하고,
승인되면 발주와 재고에 반영되는 사내 업무 시스템입니다.

> 만드는 중입니다. 단계별 진행은 Pull Request 목록을 봐 주세요.

## 기술 스택 (예정)

Java 17 · Spring Boot 3.3 · Spring Data JPA · QueryDSL · Thymeleaf · Spring Security · Apache POI · MySQL 8 · Spring Batch · JUnit 5 · Docker · GitHub Actions · AWS EC2

## 실행 방법

필요한 것: JDK 17 이상, Docker Desktop

```bash
cp .env.example .env              # DB 비밀번호 등 (처음 한 번)
docker compose up -d              # MySQL 실행
./gradlew bootRun                 # http://localhost:8080
```

- 처음 뜰 때 샘플 부서 6개, 직급 5개, 사용자 40명(페이징 확인용 직원 30명 포함), 메뉴, 공통코드 그룹 12개가 자동으로 들어갑니다.
- 샘플 계정 (비밀번호는 모두 `pass1234`)

| 아이디 | 이름 | 역할 | 확인할 수 있는 것 |
|---|---|---|---|
| `admin` | 관리자 (인사팀) | 관리자 | 사용자·부서·직급·공통코드·메뉴 권한 관리, 사용자 엑셀 다운로드 |
| `dev1`, `dev2` | 김개발, 오코드 (개발팀) | 직원 | 관리 메뉴가 안 보이고, 주소로 들어가면 403 |
| `devlead` | 이팀장 (개발팀) | 팀장 | |
| `bizhead` | 박본부 (사업본부) | 부서장 | |
| `buylead` | 강구매 (구매팀) | 구매팀 | |
- 끄기: `Ctrl+C`, MySQL까지 끄려면 `docker compose down` (데이터는 남음)

테스트 (Docker 없이 메모리 H2로 실행):

```bash
./gradlew test
```

## 로그인과 권한

- Spring Security 세션 로그인 (폼 로그인, CSRF 켬, 비밀번호는 BCrypt로 저장)
- 메뉴마다 볼 수 있는 역할을 DB에 저장하고(`menu_role`), 요청 주소로 메뉴를 찾아 권한을 검사합니다 (`MenuAuthorizationManager`).
  관리자가 화면에서 체크만 바꾸면 메뉴 노출과 URL 접근이 함께 바뀝니다.
- `/admin/**`는 메뉴 설정과 관계없이 관리자만 들어갈 수 있습니다.

## 트러블슈팅 (요약)

| # | 문제 | 해결 | 결과 |
|---|---|---|---|
| 01 | [사용자 목록 N+1](docs/troubleshooting/01-사용자-목록-N+1.md) | QueryDSL fetch join | 쿼리 11번 → 1번 |
| 02 | [컬렉션 fetch join + 페이징](docs/troubleshooting/02-공통코드-페이징-컬렉션-fetch-join.md) | batch fetch size + 오류로 막기 | 읽은 엔티티 180개 → 60개, 쿼리 12번 → 3번 |

## 프로젝트 구조

```
src/main/java/com/example/purchase
├── common/      BaseEntity(생성·수정 시각·작성자), 업무 예외, 공통 예외 처리, 엑셀 쓰기(POI), 페이지 링크
├── config/      JPA Auditing, QueryDSL, Spring Security, 샘플 데이터
├── security/    로그인 사용자(UserDetails), 메뉴 기반 URL 권한 검사
├── menu/        메뉴와 역할별 권한
├── org/         사용자·부서·직급 (조직도), 사용자 엑셀 다운로드
├── code/        공통코드 그룹·코드 (품목 분류, 단위 등)
└── home/        대시보드
src/main/resources/templates
├── layout/      공통 레이아웃 (Thymeleaf Layout Dialect)
├── fragments/   헤더, 왼쪽 메뉴 (역할에 따라 다르게 보임)
├── admin/       사용자·부서·직급·메뉴 권한 관리
└── error/       오류 화면
```

## 문서

- [요구사항 정의서](docs/01-요구사항-정의서.md)
- [ERD](docs/02-ERD.md)
- [화면 목록](docs/03-화면-목록.md)
- [결재 상태도](docs/04-결재-상태도.md)
- [트러블슈팅 기록](docs/troubleshooting/README.md)
