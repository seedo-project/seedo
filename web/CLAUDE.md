# web/ — Next.js 프론트엔드 작업 가이드

> ⚠️ **이 폴더는 아직 비어 있다.** 프론트 구조·라이브러리 선택은 팀 합의 전이며, 아래 가이드는 한 명의 제안 초안.
> 정식 결정 전까지는 이 폴더에 코드를 추가하지 않는다.

> 루트 `CLAUDE.md`(도메인·스키마·트랜잭션 규칙)와 함께 자동 로드된다. 이 파일은 **Next.js / 프론트 구현 디테일**에만 집중.

---

## 스택 (제안)

- **Next.js 15+ (App Router)** — Server Components 기본, "use client"는 인터랙션 컴포넌트에만
- **TypeScript** strict 모드
- **TailwindCSS** + **shadcn/ui** — 디자인 토큰·접근성·유지보수
- **supabase-js** (anon 키) — 로그인·단순 조회 직결
- **Spring API 클라이언트** — 트랜잭션이 필요한 호출 경유
- **react-hook-form** + **zod** — 폼 검증
- **TanStack Query** — 서버 상태 캐시 (선택)
- **마크다운 에디터: TipTap** (00 §3.1.D.1 권장)
- **메시지 스트리밍: SSE** (챗봇 finalize 단계)

## 디렉토리 (제안)

```
web/
├── package.json
├── next.config.js
├── tsconfig.json
├── tailwind.config.ts
├── src/
│   ├── app/                      ← App Router 페이지
│   │   ├── (auth)/login/
│   │   ├── (auth)/sign-up/
│   │   ├── (auth)/find-password/
│   │   ├── (main)/idea/          ← 아이디어 피드
│   │   ├── (main)/idea-upload/   ← 챗봇 작성
│   │   ├── (main)/idea-page/     ← 아이디어 상세
│   │   ├── (main)/feed/          ← 프로젝트 피드
│   │   ├── (main)/feed-page/
│   │   ├── (main)/project-upload/
│   │   ├── (main)/board/
│   │   ├── (main)/board-upload/
│   │   ├── (main)/board-page/
│   │   ├── (main)/my-page/
│   │   └── api/                  ← Spring 프록시 또는 BFF
│   ├── components/
│   │   ├── ui/                   ← shadcn/ui 컴포넌트
│   │   ├── idea/                 ← 도메인별 컴포넌트
│   │   ├── project/
│   │   ├── post/
│   │   └── shared/               ← Header, Footer, Layout
│   ├── lib/
│   │   ├── supabase/             ← createClient (server/client/middleware 분리)
│   │   ├── api/                  ← Spring API 클라이언트 (fetch wrapper)
│   │   └── utils/                ← cn, formatters 등
│   ├── hooks/                    ← useAuth, useIdea, ...
│   └── types/                    ← API 응답 타입 (OpenAPI 자동 생성 권장)
└── public/
```

## 책임 분담 (루트 `docs/03_RESPONSIBILITY_SPLIT.md` 참조)

| 작업 | 호출 방식 |
|---|---|
| 로그인·회원가입·로그아웃 | `supabase-js` 직결 |
| 프로필·잔액·거래내역 조회 | `supabase-js` 직결 (RLS) |
| 아이디어 피드·상세 조회 | `supabase-js` 직결 |
| 게시판 CRUD, Hype/Follow | `supabase-js` 직결 |
| 챗봇 메시지·finalize | **Spring API** (LLM) |
| 크레딧 충전 (PG webhook 트리거) | **Spring API** |
| 아이디어 구매·채택 | **Spring API** (트랜잭션) |
| 자연어 검색 | **Spring API** (RAG) |

## Supabase 클라이언트 분리 (Next.js App Router 표준)

```
src/lib/supabase/
├── client.ts        ← createBrowserClient (Client Component용)
├── server.ts        ← createServerClient (Server Component·Route Handler용)
└── middleware.ts    ← updateSession (인증 쿠키 갱신)
```

`@supabase/ssr` 패키지 사용. `@supabase/auth-helpers-nextjs`는 deprecated.

## Spring API 호출 패턴

```typescript
// src/lib/api/client.ts
const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL!;

export async function springFetch(path: string, init?: RequestInit) {
  const supabase = createServerClient();
  const { data: { session } } = await supabase.auth.getSession();

  return fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      ...init?.headers,
      'Content-Type': 'application/json',
      Authorization: session ? `Bearer ${session.access_token}` : '',
    },
  });
}
```

## 환경 변수

```
NEXT_PUBLIC_SUPABASE_URL
NEXT_PUBLIC_SUPABASE_ANON_KEY        # 클라이언트 노출 OK (RLS로 보호)
NEXT_PUBLIC_API_BASE_URL             # Spring API base
SUPABASE_SERVICE_ROLE_KEY            # ⚠️ 클라이언트 절대 노출 금지, Route Handler에서만
```

## 컨벤션

- **Server Component 기본**, 인터랙션 필요한 곳만 `"use client"`
- **데이터 페칭은 Server Component**에서 — Supabase server client 또는 Spring API
- **상태 관리**: 서버 상태는 RSC + revalidation, 클라이언트 상태는 useState/useReducer (Zustand는 정말 필요할 때만)
- **폼**: react-hook-form + zod 스키마. 서버 검증은 Spring/Supabase에서 한 번 더
- **에러 처리**: `error.tsx`, `not-found.tsx` 라우트별 정의
- **인증 상태**: middleware로 보호 라우트 검사, `(main)` 그룹은 인증 필수
- **i18n**: 한국어만 (MVP)

## 테스트 (선택)

- 단위: Vitest + React Testing Library
- E2E: Playwright (핵심 플로우 — 로그인, 아이디어 작성, 구매)
- MVP에선 E2E 1~2개만 갖추고 운영하면서 추가
