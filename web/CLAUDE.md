# web/ — Next.js 프론트엔드 작업 가이드

> 루트 `CLAUDE.md`(도메인·스키마·트랜잭션 규칙)와 함께 자동 로드. 이 파일은 **Next.js / 프론트 구현 디테일**에 집중.

## 0. 작업자 컨텍스트 (중요)

- **단독 작업** — 프론트는 1인. 팀 리뷰 없음.
- **사용자가 코드를 직접 읽지 않음** — Claude Code로 개발하고 결과는 브라우저로 확인.
- 따라서 **모든 페이지·기능 작업의 마지막 단계는 항상 브라우저 검증** (claude-in-chrome MCP). 단순 빌드 통과 ≠ 완료.
- 코드 정렬·주석·네이밍 일관성은 사람 가독성용 도구(Prettier, Husky)에 의존하지 않고 **이 CLAUDE.md의 규칙으로 직접 관리**.

## 1. 확정 스택

| 영역 | 버전/선택 | 비고 |
|---|---|---|
| Framework | **Next.js 16.2+** (App Router) | `middleware`는 deprecated → **`proxy`** 사용 |
| Language | TypeScript 5.9, **strict** | `tsconfig.json` `incremental: true` |
| UI | React 19.2 | App Router는 React canary 사용 (package 버전과 무관) |
| Styling | **Tailwind CSS v4** | `tailwind.config.ts` **없음**. theme는 `src/app/globals.css`의 `@theme inline { ... }`에 CSS 변수로. |
| Component | **shadcn/ui** (style: `base-nova`) | `@base-ui/react` 기반 (Radix 아님). baseColor: `neutral` |
| Icons | `lucide-react` 1.x | base-nova가 가져옴 |
| Auth/DB client | `@supabase/ssr` + `@supabase/supabase-js` | `@supabase/auth-helpers-nextjs`는 deprecated |
| Form | `react-hook-form` + `zod` + `@hookform/resolvers` | shadcn `form` 컴포넌트는 base-nova에 없어 직접 조합 |
| Package manager | **pnpm** | npm 금지 (lockfile 충돌) |
| Bundler | Turbopack (Next 16 기본) | `next.config.ts`의 `turbopack.root` 명시 |

## 2. 디렉토리 구조 (실제)

```
web/
├── package.json
├── next.config.ts            ← turbopack.root 고정 (lockfile 모호 방지)
├── tsconfig.json             ← strict + paths "@/*" → src/*
├── components.json           ← shadcn 설정
├── postcss.config.mjs        ← @tailwindcss/postcss
├── eslint.config.mjs         ← Next 기본 (확장 안 함)
├── .env.example
├── AGENTS.md                 ← Next.js 자동 생성: "이 Next는 너가 알던 게 아니다"
├── src/
│   ├── app/
│   │   ├── layout.tsx, page.tsx (→ /idea로 redirect), globals.css, favicon.ico
│   │   ├── (auth)/
│   │   │   ├── login/page.tsx           ← S101
│   │   │   ├── sign-up/page.tsx         ← S102
│   │   │   └── find-password/page.tsx   ← S103
│   │   └── (main)/
│   │       ├── idea/page.tsx            ← S201
│   │       ├── feed/page.tsx            ← S301
│   │       ├── board/page.tsx
│   │       └── my-page/page.tsx
│   ├── components/
│   │   ├── ui/                          ← shadcn (button, input, card, badge, checkbox, label, separator)
│   │   ├── idea/, project/, post/, shared/  ← 도메인별 (현재 비어있음)
│   ├── lib/
│   │   ├── supabase/
│   │   │   ├── client.ts                ← createBrowserClient (Client Component)
│   │   │   ├── server.ts                ← createServerClient (Server Component / Route Handler)
│   │   │   └── proxy.ts                 ← updateSession (인증 가드 + 쿠키 갱신)
│   │   ├── api/
│   │   │   └── client.ts                ← springFetch() — Spring API 호출 + JWT 자동 첨부
│   │   └── utils.ts                     ← shadcn cn()
│   ├── hooks/, types/                   ← 빈 폴더
│   └── proxy.ts                         ← Next 16 컨벤션 (구 middleware.ts). updateSession 위임
└── public/
```

## 3. 라우팅 컨벤션

- **의미 URL** 사용 — `/login`, `/sign-up`, `/find-password`, `/idea`, `/feed`, `/board`, `/my-page`. Figma 화면 코드(S101 등)는 **참조용**으로만 주석에.
- 라우트 그룹 `(auth)` / `(main)`은 인증 가드 분리용. URL에 안 나타남.
- 루트 `/` → `/idea`로 redirect. 미인증이면 proxy가 `/login`으로 다시 리다이렉트.

## 4. 책임 분담 (루트 `docs/03_RESPONSIBILITY_SPLIT.md` 참조)

| 작업 | 호출 방식 |
|---|---|
| 로그인·회원가입·로그아웃 | `supabase-js` 직결 (`@/lib/supabase/client` or `server`) |
| 프로필·잔액·거래내역 조회 | `supabase-js` 직결 (RLS) |
| 아이디어·프로젝트 피드 조회 | `supabase-js` 직결 |
| 게시판 CRUD, Hype/Follow | `supabase-js` 직결 |
| 챗봇 메시지·finalize | **Spring API** (`springFetch`) |
| 크레딧 충전·구매·채택 | **Spring API** (트랜잭션) |
| 자연어 검색 | **Spring API** (RAG) |

## 5. 컴포넌트 컨벤션

- **Server Component 기본**. `"use client"`는 인터랙션 컴포넌트(form, 토글, 차트 등)에만.
- 데이터 페칭은 Server Component에서 — Supabase server client 또는 `springFetch`.
- 에러 처리는 라우트별 `error.tsx`, `not-found.tsx`로.
- 파일명: 컴포넌트 파일 `kebab-case.tsx`, 컴포넌트 export는 `PascalCase`.
- 주석 최소화. 자명한 코드면 주석 X. 비자명한 결정(왜)만.

## 6. Figma → 코드 워크플로

**디자인 파일 키**: `TQnIsGqNndP0kNee3EQRyf` (Seedo 웹 디자인)
**디자인 진척도**: 데스크탑 7개 (1440×1024), 모바일 0개. 컴포넌트 라이브러리 잘 구축됨.
**모바일은 Phase 1 범위 밖** — 데스크탑만.

### 페이지 작업 절차
1. 디자이너가 만든 화면의 **nodeId** 받기 (Figma URL의 `?node-id=` 부분, `-`를 `:`로 변환)
2. `mcp__figma__get_design_context`로 코드 초안 + 스크린샷 받기
3. 초안의 React+Tailwind를 **shadcn 컴포넌트로 매핑**해서 다시 작성:
   - Figma `text field` → shadcn `Input`
   - Figma `button-1/login` (variants) → shadcn `Button` (variant prop)
   - Figma `chip-status` (4 상태) → shadcn `Badge`
   - Figma `navigation bar` → 자체 `components/shared/Header`
   - Figma `project list` → `components/project/ProjectCard` + 그리드
   - Figma `text` 색상 (`slate-50/100/600`) → CSS 변수 (`text-foreground`, `text-muted-foreground` 등) 우선
4. **반드시 브라우저로 확인** — `pnpm dev` 띄우고 claude-in-chrome MCP로 navigate, 스크린샷 비교.
5. 컴포넌트 추출 우선 — 같은 마크업 두 번 나오면 즉시 `components/`로 추출.

### 컬러 토큰 메모
- shadcn baseColor가 `neutral`로 깔려 있는데 Figma는 `slate-*` 사용. **첫 페이지 구현 시 정합성 확인 후** baseColor 전환 검토 (재 init 또는 globals.css 수동 갱신).
- brand 컬러가 따로 정의돼 있으면 `globals.css`의 `@theme inline`에 CSS 변수로 추가.

## 7. Supabase 클라이언트 사용 패턴

```typescript
// Server Component에서
import { createClient } from "@/lib/supabase/server";
const supabase = await createClient();   // ⚠ Next 15+에서 cookies()는 async
const { data } = await supabase.from("ideas").select("...");

// Client Component에서
"use client";
import { createClient } from "@/lib/supabase/client";
const supabase = createClient();
```

## 8. Spring API 호출 패턴

```typescript
// Server Component / Route Handler에서만 (JWT는 서버 쿠키에서)
import { springFetch } from "@/lib/api/client";
const res = await springFetch("/api/v1/ideas/123/purchase", { method: "POST" });
```

## 9. 환경 변수

```
NEXT_PUBLIC_SUPABASE_URL              # 클라이언트 노출 OK
NEXT_PUBLIC_SUPABASE_ANON_KEY         # 클라이언트 노출 OK (RLS로 보호)
NEXT_PUBLIC_API_BASE_URL              # Spring API base
```
- `SUPABASE_SERVICE_ROLE_KEY`는 Next 쪽 절대 사용 금지 (Spring 전용).

## 10. 자주 쓰는 명령

```sh
pnpm dev                # 개발 서버 (Turbopack)
pnpm build              # 프로덕션 빌드 + 타입체크 (= 1차 검증)
pnpm lint               # ESLint
pnpm exec tsc --noEmit  # 타입체크만 (PostToolUse 훅이 자동 실행 중)
pnpm dlx shadcn@latest add <component>  # shadcn 컴포넌트 추가
```

## 11. 자동 동작 (Claude Code 훅)

- **PostToolUse on Edit/Write/MultiEdit** → `web/`의 `.ts/.tsx` 편집 직후 `tsc --noEmit --incremental` 자동 실행. 타입 에러 있으면 다음 턴에 자동 피드백 → 사용자 거치지 않고 자체 수정.
- 훅 스크립트: `.claude/hooks/typecheck-web.sh` (레포 루트 기준 상대경로)

## 12. Next 16 / Tailwind v4 / shadcn base-nova 주의사항

- **`middleware.ts` ❌ → `proxy.ts` ✅**, 함수명 `middleware` → `proxy`.
- `cookies()`, `headers()`, `params`, `searchParams` 모두 **async** (Next 15+).
- Tailwind v4는 `tailwind.config.ts` **없음**. theme는 CSS의 `@theme inline`. 새 토큰 추가는 `globals.css`에서.
- shadcn `base-nova` 스타일은 `@base-ui/react` 기반 (Radix와 prop 조금 다를 수 있음). 컴포넌트 코드 직접 확인.
- shadcn `form` 컴포넌트는 base-nova 레지스트리에 없음 → react-hook-form + zod로 직접 조합.
- 의심 시 항상 `/Users/jiseongmin/seedo/web/node_modules/next/dist/docs/`의 로컬 Next 16 docs 참조 (학습 데이터와 다름).

## 13. 안 쓰기로 한 것 (의도적 결정)

- ❌ Prettier / Husky / lint-staged — 1인 개발 + 사람이 코드 안 읽음. 효용 없음.
- ❌ Storybook — shadcn 자체가 카탈로그 역할.
- ❌ Vitest/Playwright — MVP 단계. 핵심 페이지 안정화 후 도입.
- ❌ Zustand 등 전역 상태 라이브러리 — 서버 상태는 RSC, 클라 상태는 useState로 시작.

## 14. 알려진 후속 거리 (TODO — 디자이너/사용자 액션 필요)

- **소셜 로그인 아이콘 (Kakao/Apple)** — 현재 Wikimedia Commons 공식 SVG 인라인.
  Figma 디자인과 거의 일치하지만 100% 매칭하려면 디자이너가 export한
  카카오/애플 자산 필요. `src/components/auth/social-icons.tsx` 참조.
- **로그인 폼 인증 동작** — 현재 `.env.local`에 placeholder 값. 실제 Supabase
  프로젝트 만들고 `NEXT_PUBLIC_SUPABASE_URL`/`ANON_KEY` 교체하면 작동.
