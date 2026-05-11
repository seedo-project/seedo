#!/usr/bin/env bash
# PreToolUse hook (Bash): gh issue/pr create|edit 의 --body 가 레포 템플릿 섹션을 포함하는지 검증.
# 누락 시 stderr 출력 + exit 2 로 차단.

set -euo pipefail

payload=$(cat)
command=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')

# 1) gh issue|pr create|edit 만 가로챈다
if ! printf '%s' "$command" | grep -qE 'gh[[:space:]]+(issue|pr)[[:space:]]+(create|edit)'; then
  exit 0
fi

# 2) --body 가 명시되어 있을 때만 검증 (라벨/상태만 바꾸는 edit 는 통과)
if ! printf '%s' "$command" | grep -qE -- '(^|[[:space:]])(--body|-b|--body-file|-F)([[:space:]]|=)'; then
  exit 0
fi

# 3) 종류 판별
kind=""
template_path=""
if printf '%s' "$command" | grep -qE 'gh[[:space:]]+pr[[:space:]]+(create|edit)'; then
  kind="pr"
  template_path=".github/pull_request_template.md"
elif printf '%s' "$command" | grep -qE 'gh[[:space:]]+issue[[:space:]]+(create|edit)'; then
  if printf '%s' "$command" | grep -qE -- '--label[[:space:]]+feature(\b|[[:space:]]|"|$)'; then
    kind="issue-feature"
    template_path=".github/ISSUE_TEMPLATE/feature_request.yml"
  elif printf '%s' "$command" | grep -qE -- '--label[[:space:]]+bug(\b|[[:space:]]|"|$)'; then
    kind="issue-bug"
    template_path=".github/ISSUE_TEMPLATE/bug_report.yml"
  else
    # `gh issue edit <num>` — 라벨이 명령에 없으면 이슈 번호로 조회
    num=$(printf '%s' "$command" | sed -nE 's/.*gh[[:space:]]+issue[[:space:]]+edit[[:space:]]+([0-9]+).*/\1/p')
    if [ -n "${num:-}" ] && command -v gh >/dev/null 2>&1; then
      labels=$(gh issue view "$num" --json labels -q '.labels[].name' 2>/dev/null || echo "")
      if printf '%s' "$labels" | grep -qx 'feature'; then
        kind="issue-feature"
        template_path=".github/ISSUE_TEMPLATE/feature_request.yml"
      elif printf '%s' "$labels" | grep -qx 'bug'; then
        kind="issue-bug"
        template_path=".github/ISSUE_TEMPLATE/bug_report.yml"
      else
        exit 0
      fi
    else
      exit 0
    fi
  fi
fi

# 4) 필수 섹션 헤더 — 템플릿과 일치해야 함
case "$kind" in
  issue-feature)
    required=( '## 📌 개요' '## ✅ 할 일 목록' '## 💬 추가 사항' )
    ;;
  issue-bug)
    required=( '## 🚨 버그 상황' '## 🔁 재현 방법' '## 💡 예상 결과' )
    ;;
  pr)
    required=( '## 📌 개요' '## 🔍 변경 사항' '## 🔗 관련 이슈' '## 🧪 테스트 결과' '## ✅ 체크리스트' )
    ;;
esac

# 5) command 문자열 전체에서 각 헤더가 등장하는지 grep (HEREDOC / --body "..." 모두 커버)
missing=()
for section in "${required[@]}"; do
  if ! printf '%s' "$command" | grep -qF "$section"; then
    missing+=( "$section" )
  fi
done

if [ ${#missing[@]} -gt 0 ]; then
  {
    echo "[check-gh-template] $kind body 가 레포 템플릿(${template_path}) 의 필수 섹션을 누락."
    echo "누락된 섹션 헤더:"
    for s in "${missing[@]}"; do
      echo "  - $s"
    done
    echo ""
    echo "본문에 위 헤더를 추가하고 다시 시도하세요. (PR 본문은 'Close #<이슈번호>' 를 '## 🔗 관련 이슈' 안에 넣을 것)"
  } >&2
  exit 2
fi

exit 0
