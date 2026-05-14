#!/usr/bin/env bash
# 관리자가 사용자에게 크레딧을 적립하는 스크립트.
# Spring API POST /api/v1/admin/credit/grant 호출 — 잔액 + ADJUST 원장이 한 트랜잭션에서 처리됨.
#
# 사용:
#   scripts/grant-credit.sh <user-uuid> <amount> [reason]
#
# 환경변수 (선택):
#   SEEDO_ADMIN_EMAIL     — 관리자 계정 이메일 (default: yaung.jin.mo@gmail.com)
#   SEEDO_ADMIN_PASSWORD  — 비밀번호. 미설정 시 prompt 로 안 보이게 입력
#   SEEDO_API_BASE        — Spring API base URL (default: http://localhost:8080)
#   SEEDO_SUPABASE_URL    — Supabase project URL
#   SEEDO_SUPABASE_ANON_KEY — Supabase anon public key
#
# 한 번에 여러 번 사용하려면 비번 export 후 호출:
#   export SEEDO_ADMIN_PASSWORD='...'
#   scripts/grant-credit.sh <uuid1> 500 "테스트1"
#   scripts/grant-credit.sh <uuid2> 1000 "테스트2"
#
# 의존: curl, jq. (jq 없으면: brew install jq)

set -euo pipefail

USER_ID="${1:-}"
AMOUNT="${2:-}"
REASON="${3:-관리자 적립}"

if [[ -z "$USER_ID" || -z "$AMOUNT" ]]; then
    cat >&2 <<EOF
사용: $0 <user-uuid> <amount> [reason]

예시:
  $0 95ce6b61-57d8-4c8d-905e-06bb3e3e603e 1000 "온보딩 크레딧"

환경변수 SEEDO_ADMIN_PASSWORD 가 없으면 비밀번호를 안 보이게 입력 받습니다.
EOF
    exit 1
fi

# amount 사전 검증 — jq --argjson 이 숫자 외 입력에 파싱 오류로 죽기 전에 명확히 안내 (CodeRabbit #182).
if ! [[ "$AMOUNT" =~ ^[0-9]+$ ]] || (( AMOUNT <= 0 )); then
    echo "❌ amount 는 0 보다 큰 정수여야 합니다: $AMOUNT" >&2
    exit 1
fi

EMAIL="${SEEDO_ADMIN_EMAIL:-yaung.jin.mo@gmail.com}"
API_BASE="${SEEDO_API_BASE:-http://localhost:8080}"
SUPABASE_URL="${SEEDO_SUPABASE_URL:-https://lkwwfwgieffwoeqiliqa.supabase.co}"
# anon public key — 클라이언트 노출 OK (RLS 가드 + apikey 검증용). service_role 키 절대 X.
SUPABASE_ANON_KEY="${SEEDO_SUPABASE_ANON_KEY:-eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imxrd3dmd2dpZWZmd29lcWlsaXFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgzMTY5MDYsImV4cCI6MjA5Mzg5MjkwNn0.2-nDu5bFhy5e02RyHcH5PPCTjTbhYQTorZ6FcZNfvtk}"

if [[ -z "${SEEDO_ADMIN_PASSWORD:-}" ]]; then
    read -rsp "Password for ${EMAIL}: " SEEDO_ADMIN_PASSWORD
    echo
fi

# curl 공통 옵션: connect 10s, 전체 30s 한계 — 무한 블로킹 방지 (CodeRabbit #182).
CURL_OPTS=(--silent --show-error --connect-timeout 10 --max-time 30)

# 1. Supabase Auth 로그인 → JWT 발급
# 비밀번호를 jq/curl 인자에 안 노출하기 위해 stdin 경유 (CodeRabbit #182).
# - jq: 비밀번호는 here-string 으로 stdin 입력 → `input` 으로 읽음. --arg 사용 X
# - curl: --data-binary @- 로 stdin 입력 → -d "<password 포함된 변수>" 사용 X
JWT=$(jq -nR --arg e "$EMAIL" '{email:$e, password:input}' <<< "$SEEDO_ADMIN_PASSWORD" \
    | curl "${CURL_OPTS[@]}" -X POST "${SUPABASE_URL}/auth/v1/token?grant_type=password" \
        -H "apikey: ${SUPABASE_ANON_KEY}" \
        -H "Content-Type: application/json" \
        --data-binary @- \
    | jq -r '.access_token // empty')

if [[ -z "$JWT" ]]; then
    echo "❌ 로그인 실패 — 이메일/비밀번호 또는 anon key 확인" >&2
    exit 1
fi

# 2. Spring API 호출 — grant body 는 비밀번호 없어 단순 변수로 OK.
GRANT_BODY=$(jq -n --arg u "$USER_ID" --argjson a "$AMOUNT" --arg r "$REASON" \
    '{userId:$u, amount:$a, reason:$r}')

RESPONSE=$(curl "${CURL_OPTS[@]}" -X POST "${API_BASE}/api/v1/admin/credit/grant" \
    -H "Authorization: Bearer ${JWT}" \
    -H "Content-Type: application/json" \
    -d "$GRANT_BODY" \
    -w "\n__HTTP_CODE__:%{http_code}")

HTTP_CODE=$(echo "$RESPONSE" | tail -1 | sed 's/__HTTP_CODE__://')
BODY=$(echo "$RESPONSE" | sed '$d')

# jq 가 비-JSON 응답에서 실패해도 set -e 로 죽지 않게 fallback (CodeRabbit #182).
print_body() {
    echo "$BODY" | jq . 2>/dev/null || echo "$BODY"
}

if [[ "$HTTP_CODE" == "200" ]]; then
    echo "✅ 적립 성공:"
    print_body
else
    echo "❌ 실패 (HTTP $HTTP_CODE):" >&2
    print_body >&2
    if [[ "$HTTP_CODE" == "403" ]]; then
        echo "" >&2
        echo "→ ADMIN role 부여 안 됐을 수 있습니다. Supabase SQL Editor 에서:" >&2
        echo "  INSERT INTO public.user_roles (user_id, role_id) VALUES ('<본인 UUID>', 2) ON CONFLICT DO NOTHING;" >&2
    fi
    exit 1
fi
