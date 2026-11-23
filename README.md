# 구매요청·전자결재 시스템 (purchase-approval)

직원이 구매요청서를 쓰면 팀장 → 부서장 → (500만 원 이상이면) 구매팀장 순서로 결재하고,
승인되면 발주와 재고에 반영되는 사내 업무 시스템입니다.

> 만드는 중입니다. 단계별 진행은 Pull Request 목록을 봐 주세요.

## 기술 스택 (예정)

Java 17 · Spring Boot 3.3 · Spring Data JPA · QueryDSL · Thymeleaf · Spring Security · MySQL 8 · Spring Batch · JUnit 5 · Docker · GitHub Actions · AWS EC2

## 실행 방법

필요한 것: JDK 17 이상, Docker Desktop

```bash
cp .env.example .env              # DB 비밀번호 등 (처음 한 번)
docker compose up -d              # MySQL 실행
./gradlew bootRun                 # http://localhost:8080
```

- 처음 뜰 때 샘플 부서 6개와 직급 5개가 자동으로 들어갑니다.
- 끄기: `Ctrl+C`, MySQL까지 끄려면 `docker compose down` (데이터는 남음)

테스트 (Docker 없이 메모리 H2로 실행):

```bash
./gradlew test
```

## 프로젝트 구조

```
src/main/java/com/example/purchase
├── common/      BaseEntity(생성·수정 시각·작성자), 업무 예외, 공통 예외 처리
├── config/      JPA Auditing, QueryDSL, 로컬 샘플 데이터
├── org/         부서·직급 (조직도)
└── home/        대시보드
src/main/resources/templates
├── layout/      공통 레이아웃 (Thymeleaf Layout Dialect)
├── fragments/   헤더, 왼쪽 메뉴
└── error/       오류 화면
```

## 문서

- [요구사항 정의서](docs/01-요구사항-정의서.md)
- [ERD](docs/02-ERD.md)
- [화면 목록](docs/03-화면-목록.md)
- [결재 상태도](docs/04-결재-상태도.md)
- [트러블슈팅 기록](docs/troubleshooting/README.md)
