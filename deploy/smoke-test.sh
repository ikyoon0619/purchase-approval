#!/usr/bin/env bash
# 배포 후 확인: 로그인 화면 → 로그인 → 첫 화면·관리자 화면이 200인지.
# 사용: ./deploy/smoke-test.sh http://서버주소 admin 비밀번호
set -euo pipefail
BASE=${1:?주소}; USER_ID=${2:?아이디}; PASS=${3:?비밀번호}
JAR=$(mktemp); trap 'rm -f "$JAR"' EXIT

# 앱이 막 떴을 때는 시작 작업(관리자·샘플 데이터 넣기)이 아직 도는 중일 수 있어서 몇 번 다시 시도한다
login() {
  rm -f "$JAR"
  local csrf
  csrf=$(curl -sf -c "$JAR" -b "$JAR" "$BASE/login" | grep -o 'name="_csrf" value="[^"]*"' | sed 's/.*value="//; s/"$//') || return 1
  [ -n "$csrf" ] || return 1
  local location
  location=$(curl -s -c "$JAR" -b "$JAR" -o /dev/null -w '%{redirect_url}' \
    --data-urlencode "loginId=$USER_ID" --data-urlencode "password=$PASS" --data-urlencode "_csrf=$csrf" "$BASE/login")
  case "$location" in *error*|"") return 1 ;; esac
}
for i in $(seq 1 10); do
  login && break
  [ "$i" = 10 ] && { echo "로그인 실패 ($USER_ID)"; exit 1; }
  sleep 3
done

for path in / /admin/members /admin/audit-logs /stats/monthly; do
  code=$(curl -s -b "$JAR" -o /dev/null -w '%{http_code}' "$BASE$path")
  echo "$path $code"
  [ "$code" = 200 ] || { echo "실패: $path → $code"; exit 1; }
done
echo "OK"
