#!/usr/bin/env bash
# PostToolUse 훅: web/ 안의 .ts/.tsx 파일을 Edit/Write/MultiEdit한 직후
# 증분 tsc --noEmit으로 타입 에러를 즉시 Claude에게 피드백한다.
#
# 동작:
#   - stdin으로 받은 JSON에서 tool_input.file_path 추출
#   - web/**/*.ts(x)가 아니면 조용히 종료
#   - 타입 에러 있으면 exit 2 + stderr (Claude가 다음 턴에 보고 자동 수정)
#   - 에러 없으면 조용히 종료

set -uo pipefail

# 스크립트 위치 기준으로 레포 루트 → web/ 디렉토리 계산 (절대경로 박지 않기)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEB_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)/web"

# stdin JSON 파싱 (jq 없으면 대충 grep)
PAYLOAD="$(cat)"
FILE_PATH=""
if command -v jq >/dev/null 2>&1; then
  FILE_PATH="$(printf '%s' "$PAYLOAD" | jq -r '.tool_input.file_path // empty' 2>/dev/null || true)"
fi

# 대상 아니면 종료
case "$FILE_PATH" in
  "$WEB_DIR"/*.ts|"$WEB_DIR"/*.tsx|"$WEB_DIR"/*.mts|"$WEB_DIR"/*.cts) ;;
  *) exit 0 ;;
esac

# tsc 증분 실행
cd "$WEB_DIR" || exit 0
TSC="$WEB_DIR/node_modules/.bin/tsc"
[[ -x "$TSC" ]] || exit 0

if ! OUTPUT="$("$TSC" --noEmit --incremental 2>&1)"; then
  {
    echo "Type check failed after editing: $FILE_PATH"
    echo "----"
    printf '%s\n' "$OUTPUT"
  } >&2
  exit 2
fi

exit 0
