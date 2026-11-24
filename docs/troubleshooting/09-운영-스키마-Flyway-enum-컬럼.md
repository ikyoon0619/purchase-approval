# 운영 DB 스키마: validate는 통과했는데 enum 값을 넣으면 "Data truncated", 고친 V1은 checksum mismatch

- 단계: 11단계 (Docker 배포, 운영 설정) / 관련 PR: #11
- 키워드: `ddl-auto: validate`, Flyway, MySQL `enum(...)`, `@Enumerated(STRING)`, CHECK 제약, migration checksum

## 상황

지금까지 로컬은 `ddl-auto: update`(JPA가 테이블을 알아서 만들고 고침), 테스트는 H2 `create-drop`이었다.
운영에서는 JPA가 테이블을 바꾸면 안 된다 (컬럼 삭제·타입 변경이 데이터 손실로 이어질 수 있고, 무엇이 바뀌었는지 기록도 없다).
그래서 운영(`prod`)은

- 테이블은 **Flyway**가 `db/migration/V1__init.sql`, `V2__...`를 순서대로 실행해서 만든다
- JPA는 `ddl-auto: validate`: 엔티티와 테이블이 맞는지 **검사만** 한다

V1은 빈 MySQL 8에 `ddl-auto=create`로 엔티티 그대로 테이블을 만든 뒤 `mysqldump --no-data`로 뽑았다.

## 증상 1: 마이그레이션 없이 prod로 띄우면 (예상한 실패)

```
Schema-validation: missing table [approval_line]
```

앱이 아예 안 뜬다. 이건 원하는 동작이다: 테이블이 안 맞으면 요청을 받기 전에 멈춘다.

## 증상 2: validate는 통과하는데, 값을 넣으면 실패한다

`mysqldump`를 보니 enum 필드가 MySQL 고유 타입으로 만들어져 있었다 (이 프로젝트의 Hibernate 6.5는 MySQL에서 `@Enumerated(STRING)`을 이렇게 만든다).

```sql
`action` enum('APPROVE','BATCH','CREATE', ... ,'WITHDRAW') NOT NULL
```

나중에 `AuditAction`에 상수를 하나 추가하고 마이그레이션을 깜빡하면 어떻게 되는지 확인했다.
DB 쪽 enum 목록에서 `BATCH`를 빼서 "코드에는 있고 DB에는 없는" 상태를 만들고:

| 확인 | 결과 |
|---|---|
| `ddl-auto: validate`로 앱 시작 | **정상 시작** (`Started PurchaseApprovalApplication`) |
| `BATCH` 값 insert | `ERROR 1265 (01000): Data truncated for column 'action' at row 1` |

즉 validate는 "컬럼이 있고 타입 계열이 맞는지"만 보고 **enum 목록은 비교하지 않는다**.
운영에서는 새 기능을 처음 쓰는 순간(야간 배치 기록 같은) 500이 난다.

### 한 번 더: `@JdbcTypeCode(SqlTypes.VARCHAR)`로 바꿨더니 CHECK 제약이 생겼다

varchar로 바꾸면 해결될 줄 알았는데, 다시 덤프하니

```sql
`status` varchar(20) NOT NULL,
CONSTRAINT `vendor_chk_1` CHECK ((`status` in ('ACTIVE','SUSPENDED','CLOSED','UNKNOWN')))
```

타입만 바뀌고 "허용 값 목록"은 CHECK 제약으로 그대로 남았다. 같은 함정이다.
(`hibernate.type.prefer_native_enum_types` 설정도 있지만 MySQL의 enum 컬럼에는 효과가 없었다.)

## 해결: `columnDefinition = "varchar(N)"`

```java
@Enumerated(EnumType.STRING)
// MySQL enum(...)이나 CHECK 없이 그냥 varchar: 상수를 추가해도 DB를 안 바꿔도 된다
@Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
private AuditAction action;
```

컬럼 정의를 직접 주면 Hibernate는 enum 타입도 CHECK도 만들지 않는다. 8곳 (요청서·결재선·발주 상태, 역할, 메뉴 역할, 거래처 상태, 재고 변경 종류, 감사 행위).
허용 값 검사는 자바 enum이 한다 (모르는 문자열은 애초에 엔티티에 못 들어간다).

트레이드오프: DB만 보고는 허용 값을 알 수 없고, SQL로 직접 넣으면 오타도 들어간다. 이 프로젝트는 데이터를 앱으로만 넣으므로 "상수 추가 = 코드만 배포"를 택했다.

## 증상 3: V1 파일을 고쳤더니 앱이 안 뜬다

위 수정 후 V1을 다시 뽑았는데, 내 컨테이너의 운영용 DB에는 **고치기 전 V1이 이미 적용**되어 있었다.

```
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 1
-> Applied to database : 1823861247
-> Resolved locally    : 159318410
Either revert the changes to the migration, or run repair to update the schema history.
```

Flyway는 실행한 파일의 체크섬을 `flyway_schema_history`에 저장해 두고, 시작할 때마다 비교한다.
**한 번 운영에 나간 마이그레이션 파일은 고치지 않는다.** 바꿀 게 있으면 `V2__설명.sql`을 추가한다.
이번에는 아직 배포 전이라 DB를 지우고 다시 만들었다 (운영이었다면 `ALTER TABLE ...`을 담은 V2).

## 증상 4: 맥에서 운영 compose를 띄웠더니 앱이 시작·재시작을 반복

맥에서 `docker compose -f docker-compose.prod.yml up -d --build`로 운영 조합을 띄웠는데, MySQL은 Healthy인데 앱이 계속 재시작했다.

원인: compose는 프로젝트 이름을 따로 안 정하면 **폴더 이름**을 쓴다. 로컬용 `docker-compose.yml`과 운영용 `docker-compose.prod.yml`이 같은 폴더라 둘 다 `purchase-approval`가 되고,
볼륨 `mysql-data`도 똑같이 `purchase-approval_mysql-data`가 된다. 운영 MySQL이 **로컬 개발 DB를 그대로 열었고**, 그 DB에는 `ddl-auto: update`로 만든 테이블은 있지만 Flyway 기록 테이블이 없다.
Flyway는 "비어 있지 않은데 기록이 없는 DB"에는 마이그레이션을 하지 않고 멈추므로(`baseline` 하라는 오류) 앱이 죽고, `restart: unless-stopped`로 다시 뜨기를 반복했다.
(Flyway가 시작 전에 멈추므로 로컬 데이터는 바뀌지 않았다.)

CI에서는 깨끗한 러너라 로컬 볼륨이 없어서 드러나지 않았다.

해결: `docker-compose.prod.yml` 맨 위에 `name: purchase-approval-prod`. 볼륨이 `purchase-approval-prod_mysql-data`로 분리된다.

## 재발 방지: CI에서 운영과 같은 조합으로 띄운다

테스트(H2)는 JPA가 테이블을 만들기 때문에 "엔티티는 바꿨는데 V2를 안 만든" 실수를 못 잡는다.
GitHub Actions `docker-smoke` job이 운영과 똑같이 띄운다:

1. Docker 이미지 빌드 → `docker-compose.prod.yml`로 MySQL 8 + 앱
2. Flyway 실행 → `ddl-auto: validate` → 첫 관리자 계정 생성
3. `deploy/smoke-test.sh`: 로그인 → 첫 화면·관리자 화면 200
4. 앱 재시작 → "Schema is up to date" + 기존 데이터로 다시 확인

## 전후 비교

| 상황 | 처음 (`@Enumerated(STRING)`만) | 지금 (`columnDefinition = "varchar"`) |
|---|---|---|
| enum 상수 추가, 마이그레이션 안 함 | validate 통과 → 그 값을 저장할 때 `Data truncated` | 코드만 배포하면 끝 |
| 엔티티에 컬럼 추가, 마이그레이션 안 함 | CI 테스트(H2) 통과, 운영에서 처음 발견 | CI `docker-smoke`에서 `Schema-validation: missing column`으로 실패 |

## 배운 점

- `validate`는 테이블·컬럼·타입 계열까지만 본다. enum 값 목록, CHECK, 인덱스, 길이는 안 본다.
- 엔티티에서 만들어진 DDL은 반드시 한 번 덤프해서 눈으로 본다. 생각한 것과 다를 수 있다 (enum, CHECK).
- 운영에 나간 마이그레이션은 불변. 수정은 새 버전으로.
- 운영과 같은 DB·같은 방식(Docker, Flyway, validate)으로 띄우는 검사를 CI에 하나 둔다.
- compose 파일이 여러 개면 `name:`으로 프로젝트 이름을 고정한다. 볼륨·네트워크 이름이 프로젝트 이름에서 나온다.
