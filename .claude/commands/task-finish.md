---
description: 작업 마무리 — 커밋 + PR 생성 (현재 브랜치에서 이슈 번호 자동 추출)
---

현재 작업을 마무리한다. CLAUDE.md §15 협업·Git 컨벤션을 그대로 따른다.

## 순서

1. **현재 브랜치 확인**: `git branch --show-current` → 브랜치명에서 type과 이슈 번호 파싱.
   - 예: `feat/#12-login-api` → type=`feat`, 이슈번호=12
   - `main`/`master`/`dev` 브랜치면 즉시 중단: "작업 브랜치가 아닙니다. `/task-start` 먼저 실행하세요."
   - 형식이 안 맞으면 사용자에게 이슈 번호를 묻고 진행.

2. **변경사항 파악** (병렬 실행):
   - `git status`
   - `git diff` (스테이징/언스테이징 모두)
   - `git log main..HEAD` (현 브랜치의 커밋 이력)

3. **커밋 메시지 작성**: 변경 파일 리뷰해서 작성. 형식: `<type>: <한국어 설명> (#<이슈번호>)`.
   - 예: `feat: 이메일 유효성 검사 추가 (#12)`
   - 변경의 "why"에 초점, 1~2 문장.

4. **Staging**: 변경 파일을 명시적으로 add (`git add <파일1> <파일2>`). `git add -A`/`-u`/`.` **금지** (`.env`, credentials 등 사고 방지).

5. **커밋 생성**: HEREDOC + `Co-Authored-By` 포함.
   ```
   git commit -m "$(cat <<'EOF'
   <type>: <설명> (#N)

   Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
   EOF
   )"
   ```

6. **푸시**: `git push -u origin <현재브랜치>` 실행. 훅 실패 시 NEW 커밋으로 보강 (amend 금지).

7. **PR 본문 작성**:
   ```
   ## Summary
   - <1~3 bullet points — 변경의 의도>

   ## Test plan
   - [ ] <검증 항목 1>
   - [ ] <검증 항목 2>

   Closes #<이슈번호>
   ```

8. **PR 생성**: `gh pr create --base main --title "<커밋제목>" --body "<위본문>"` 실행. HEREDOC으로 body 전달.

9. **결과 보고**: PR URL 한 줄.

> 자동 머지는 하지 않는다. 리뷰 1명 승인 후 사용자가 직접 Squash and Merge (CLAUDE.md §15.4).
