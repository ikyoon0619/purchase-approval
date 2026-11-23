# AWS EC2에 Docker로 배포하기

구성: EC2 한 대(Ubuntu) 안에 Docker로 **MySQL 컨테이너 + 앱 컨테이너**. 브라우저는 `http://서버주소`(80번 포트)로 접속합니다.

```
GitHub (main 머지)
  └ Actions: 테스트 → Docker 이미지 빌드 → ghcr.io/ikyoon0619/purchase-approval:latest 에 올림
                                              │ (선택) SSH로 EC2에서 deploy.sh 실행
EC2 (Ubuntu 24.04, Docker)                     ▼
  ├ app   컨테이너  :80 → 8080   이미지를 받아서 실행. prod 프로필, Flyway, ddl-auto=validate
  └ mysql 컨테이너  (포트 안 엶)  데이터는 Docker 볼륨 mysql-data 에
```

> AWS 화면과 무료 사용 조건은 자주 바뀝니다. 아래는 2026년 11월 기준 흐름이고, 버튼 이름이 조금 달라도 같은 항목을 찾으면 됩니다.
> **요금은 계정마다 조건이 다르니 1번(예산 알림)을 꼭 먼저 하세요.**

## 직접 하셔야 하는 것 (AWS 콘솔)

### 1. 예산 알림 만들기 (요금 사고 방지)

AWS 콘솔 → **Billing and Cost Management → Budgets → 예산 생성** → "월별 비용 예산", 금액 예: 5 USD, 알림 받을 이메일.
실수로 큰 인스턴스를 켜 두면 메일이 옵니다.

### 2. 리전: 서울

오른쪽 위 리전을 **아시아 태평양(서울) ap-northeast-2**로.

### 3. 키 페어

EC2 → **키 페어 → 키 페어 생성**: 이름 `purchase-key`, 유형 RSA, 형식 `.pem` → 내려받기.
맥에서:

```bash
mv ~/Downloads/purchase-key.pem ~/.ssh/
chmod 400 ~/.ssh/purchase-key.pem     # 권한이 넓으면 ssh가 "UNPROTECTED PRIVATE KEY FILE"로 거부
```

### 4. 보안 그룹 (방화벽)

EC2 → **보안 그룹 → 보안 그룹 생성**: 이름 `purchase-sg`

| 유형 | 포트 | 소스 | 이유 |
|---|---|---|---|
| SSH | 22 | **내 IP** | 서버 접속은 내 컴퓨터에서만 |
| HTTP | 80 | 0.0.0.0/0 | 누구나 사이트 접속 |

3306(MySQL)은 **열지 않습니다**. MySQL은 같은 서버의 앱 컨테이너만 씁니다.

### 5. 인스턴스 시작

EC2 → **인스턴스 시작**

- 이름: `purchase-approval`
- AMI: **Ubuntu Server 24.04 LTS** (x86_64)
- 인스턴스 유형: `t3.micro` 또는 `t2.micro` (콘솔에 "프리 티어 사용 가능" 표시가 있는 것)
- 키 페어: `purchase-key`
- 네트워크: 기존 보안 그룹 `purchase-sg`
- 스토리지: 20 GiB gp3

시작 후 인스턴스의 **퍼블릭 IPv4 주소**를 적어 둡니다. (인스턴스를 중지했다 켜면 주소가 바뀝니다. 고정하려면 탄력적 IP. 퍼블릭 IPv4는 시간당 요금이 붙을 수 있으니 예산 알림을 보세요.)

## 서버 준비 (터미널, 처음 한 번)

```bash
ssh -i ~/.ssh/purchase-key.pem ubuntu@<퍼블릭IP>
```

### 6. 스왑 2GB (메모리 1GB 서버라서)

MySQL + Java 앱이 1GB 안에 빠듯합니다. 스왑이 없으면 메모리가 모자랄 때 리눅스가 프로세스를 강제로 죽입니다(OOM Killer).

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h      # Swap: 2.0Gi 확인
```

### 7. Docker 설치

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu     # sudo 없이 docker 쓰기
exit                               # 다시 접속해야 그룹이 적용됨
```

다시 접속해서 `docker compose version`이 나오면 됩니다.

### 8. 코드와 설정

```bash
git clone https://github.com/ikyoon0619/purchase-approval.git
cd purchase-approval
cp .env.prod.example .env
nano .env
```

`.env`에서 바꿀 것

| 키 | 값 |
|---|---|
| `DB_PASSWORD`, `DB_ROOT_PASSWORD` | 길고 서로 다른 임의 문자열 (`openssl rand -base64 24`로 만들기) |
| `ADMIN_INITIAL_PASSWORD` | 처음 admin 비밀번호 (8자 이상). 사용자가 0명일 때 한 번만 쓰임 |
| `SAMPLE_DATA` | 포트폴리오 데모로 샘플 계정을 보여 줄 거면 `true` (아래 주의) |
| `NTS_SERVICE_KEY` | 국세청 키 (없으면 비워 둠) |

> `SAMPLE_DATA=true`면 README의 샘플 계정(admin/pass1234 포함)이 생깁니다. 누구나 관리자로 들어와 데이터를 바꿀 수 있다는 뜻이니, 공개 데모 전용으로만 쓰세요.

### 9. 이미지 받을 수 있게 하기 (처음 한 번)

main에 머지되면 GitHub Actions(Deploy)가 `ghcr.io/ikyoon0619/purchase-approval`에 이미지를 올립니다.
GitHub에서 새로 만든 패키지는 **비공개**일 수 있습니다. EC2에서 `docker compose pull`이 `denied`로 실패하면 둘 중 하나:

- (간단) GitHub → 내 프로필 → **Packages → purchase-approval → Package settings → Change visibility → Public**
- (비공개 유지) GitHub에서 `read:packages` 권한만 있는 토큰(classic)을 만들고 EC2에서
  `echo <토큰> | docker login ghcr.io -u ikyoon0619 --password-stdin`

### 10. 실행

```bash
./deploy/deploy.sh
```

`git pull` → 이미지 받기 → 컨테이너 시작 → 로그인 화면이 뜰 때까지 기다립니다. 처음에는 MySQL 초기화 때문에 1~2분 걸립니다.

확인:

```bash
./deploy/smoke-test.sh http://localhost admin '<ADMIN_INITIAL_PASSWORD>'
```

브라우저에서 `http://<퍼블릭IP>` → admin 로그인 → **사용자 관리 → admin 수정**에서 비밀번호를 바꾸고, `.env`의 `ADMIN_INITIAL_PASSWORD`는 지워도 됩니다.
그다음 부서·직급·사용자를 등록합니다.

## (선택) main 머지 → 자동 배포

GitHub 저장소 → **Settings → Secrets and variables → Actions**

| 종류 | 이름 | 값 |
|---|---|---|
| Secret | `EC2_HOST` | 퍼블릭 IP (탄력적 IP 권장) |
| Secret | `EC2_USER` | `ubuntu` |
| Secret | `EC2_SSH_KEY` | `purchase-key.pem` 파일 내용 전체 (`cat ~/.ssh/purchase-key.pem`) |
| Variable | `DEPLOY_ENABLED` | `true` |

**함정:** 4번에서 SSH(22)를 "내 IP"로만 열었으므로 GitHub Actions 서버는 접속하지 못합니다 (`Connection timed out`).
선택지:

- 자동 배포를 쓰지 않고, 머지 후 EC2에서 `./deploy/deploy.sh`를 직접 실행 (이미지 빌드·업로드는 자동)
- 22번을 0.0.0.0/0으로 열기: Ubuntu AMI는 비밀번호 로그인이 꺼져 있고 키로만 들어갈 수 있지만, 접속 시도 로그가 많이 쌓입니다

`DEPLOY_ENABLED`가 없으면 Deploy workflow의 배포 단계는 건너뛰고 이미지 업로드까지만 합니다.

## 운영 중 자주 쓰는 명령

```bash
cd ~/purchase-approval
docker compose -f docker-compose.prod.yml ps                 # 상태
docker compose -f docker-compose.prod.yml logs -f --tail 100 app   # 앱 로그
docker compose -f docker-compose.prod.yml exec mysql \
  sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" purchase'    # DB 접속

# 백업 (DB 전체를 파일로)
docker compose -f docker-compose.prod.yml exec -T mysql \
  sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction purchase' > backup-$(date +%F).sql

# 이전 버전으로 되돌리기: Actions에서 올린 커밋 해시 태그를 지정
APP_IMAGE=ghcr.io/ikyoon0619/purchase-approval:<커밋해시> docker compose -f docker-compose.prod.yml up -d --no-build app
```

## 안 될 때

| 증상 | 볼 곳 |
|---|---|
| 브라우저에서 접속 안 됨 (시간 초과) | 보안 그룹에 80번이 열려 있는지, 주소가 `http://`인지 (`https://` 아님) |
| `docker compose pull`이 `denied` | 9번 (패키지 공개 또는 docker login) |
| 앱이 계속 재시작 | `logs app`. `Schema-validation`이면 마이그레이션 누락 (트러블슈팅 09), `Communications link failure`면 MySQL이 아직 준비 중이거나 `.env` 비밀번호가 다름 |
| `.env` 비밀번호를 나중에 바꿨더니 DB 접속 실패 | MySQL 비밀번호는 **볼륨을 처음 만들 때만** 적용됩니다. 바꾸려면 MySQL 안에서 `ALTER USER`, 또는 데이터를 버려도 되면 `docker compose -f docker-compose.prod.yml down -v` 후 다시 |
| 서버가 멈춘 듯 느림 / 앱이 갑자기 죽음 | `free -h`로 스왑 확인 (6번), `dmesg | grep -i kill` |
| 감사 로그 IP가 전부 같은 값 | 앞에 Nginx·로드밸런서를 두면 `X-Forwarded-For`를 넘겨야 합니다 (prod는 `server.forward-headers-strategy: native`로 받을 준비가 되어 있음) |

## 정리 (요금 멈추기)

- 잠시 안 쓸 때: 인스턴스 **중지** (디스크 요금은 계속)
- 다 끝났을 때: 인스턴스 **종료**, 탄력적 IP를 만들었다면 **릴리스**, 남은 EBS 볼륨·스냅샷 삭제

## 다음에 할 수 있는 것

- 도메인 연결 + HTTPS (Nginx 컨테이너 + Let's Encrypt, 또는 ALB + ACM 인증서)
- MySQL을 RDS로 분리 (서버를 바꿔도 데이터 유지, 자동 백업)
