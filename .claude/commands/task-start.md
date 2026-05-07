---
description: 새 작업 시작 — GitHub 이슈 생성 + 브랜치 생성 (CLAUDE.md §15 컨벤션)
argument-hint: <type> "<설명>"
---

새 작업을 시작한다. 인자: $ARGUMENTS

CLAUDE.md §15 협업·Git 컨벤션을 그대로 따른다.

## 순서

1. **type 검증**: 인자에서 첫 토큰을 `type`으로 추출. 허용값: `feat` / `fix` / `docs` / `refactor` / `chore` / `style` / `test`. 잘못되었으면 에러로 중단하고 사용법 안내 (`/task-start feat "로그인 API 구현"`).

2. **이슈 제목 작성**: 나머지 인자를 `설명`으로 사용. type을 PascalCase로 변환 → 제목 형식 `[<TypePascal>] <설명>`.
   - 예: `feat` + `로그인 API 구현` → `[Feat] 로그인 API 구현`

3. **라벨 매핑**:
   - `feat` → `feature`
   - `fix` → `bug`
   - `docs` → `documentation`
   - `refactor` → `refactor`
   - `chore` → `chore`
   - `style` → `style`
   - `test` → `test`

4. **이슈 생성**: `gh issue create --title "<제목>" --label <라벨>` 실행. 라벨이 없다는 에러가 나면 라벨 없이 재시도. 출력에서 이슈 URL과 번호 파싱.

5. **슬러그 생성**: 설명을 영문 케밥-케이스로 변환 (한글이면 적절히 영문 의미로). 5단어 이내. 예: `로그인 API 구현` → `login-api`.

6. **브랜치 생성**: 현재 브랜치가 `main` 인지 확인 후 (아니면 사용자에게 한 번 확인) `git checkout -b <type>/#<이슈번호>-<슬러그>` 실행.
   - 예: `feat/#12-login-api`

7. **결과 보고**: 이슈 URL + 새 브랜치명 + "이제 작업 시작하세요" 한 줄.

이후 일반 대화·구현 모드로 복귀한다.
