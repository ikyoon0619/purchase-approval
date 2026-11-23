#!/usr/bin/env bash
# EC2 안에서 실행: 최신 설정 받기 → 최신 이미지 받기 → 바뀐 컨테이너만 다시 띄우기
# 사용: cd ~/purchase-approval && ./deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

git pull --ff-only
docker compose -f docker-compose.prod.yml pull app
docker compose -f docker-compose.prod.yml up -d --no-build
docker image prune -f > /dev/null   # 이전 이미지 정리 (디스크 8GB라 쌓이면 금방 찬다)

echo "앱이 뜨기를 기다립니다..."
for i in $(seq 1 60); do
  if curl -sf -o /dev/null http://localhost/login; then
    echo "배포 완료: $(docker compose -f docker-compose.prod.yml images app --format '{{.Repository}}:{{.Tag}}' 2>/dev/null || true)"
    exit 0
  fi
  sleep 3
done
echo "3분 안에 앱이 뜨지 않았습니다. 로그를 보세요:"
docker compose -f docker-compose.prod.yml logs --tail 100 app
exit 1
