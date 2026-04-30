# Seedo — 팀 킥오프 미팅 자료

> 다른 개발자분들과의 첫 합의 미팅 진행 가이드.
> 이 문서를 기반으로 슬라이드를 만들거나 그대로 화면 공유하며 진행해도 됨.
> 각 섹션이 슬라이드 한 장에 대응하도록 짧게 정리. 상세 근거는 각 docs 링크 참조.

---

## Slide 1 — Seedo란?

> 일상 속 니즈를 가진 사용자(소비자)와 프로덕트 개발자를 잇는
> **Pain Point 마켓플레이스 + 프로젝트 크라우드펀딩** 플랫폼.

**3단계 흐름**
1. 사용자가 AI 챗봇과 대화하며 자신의 니즈를 정형화된 기획문서로 만든다
2. 개발자는 크레딧을 지불하고 그 문서를 열람·채택한다
3. 채택 시 작성자에게 크레딧 보상, 아이디어는 프로젝트로 전환

---

## Slide 2 — 도메인 모델 한 장

```
User ──작성──▶ Idea ──채택──▶ Project
  │             │              │
  │             │              ├─ Members / Followers / Hypes
  │             │              └─ Posts (홍보·모집)
  │             │
  │             ├─ Documents (버전별 본문)
  │             ├─ ChatSessions (AI 대화 로그)
  │             ├─ Embeddings (검색용)
  │             └─ Purchases (열람권)
  │
  ├─ Posts ──── Applications (베타테스터/개발자 모집)
  ├─ Credits (잔액 + 원장)
  └─ Roles (RBAC)
```

→ 18개 테이블. 전체 ERD: [`05_ERD.md`](05_ERD.md)

---

## Slide 3 — 핵심 화면 흐름

| 화면 | 라우트 | 주요 동작 |
|---|---|---|
| 로그인·가입 | `/`, `/sign-up` | Supabase Auth |
| 아이디어 피드 | `/idea` | 검색·필터·구매 진입 |
| 아이디어 작성 (챗봇) | `/idea-upload` | LLM 대화 → finalize |
| 아이디어 상세 | `/idea-page?id=` | 키워드 미리보기 → 결제 → 본문 |
| 프로젝트 피드 | `/feed` | Hype/Follow |
| 게시판 | `/board` | 자유 / 홍보 / 베타·개발자 모집 |
| 마이페이지 | `/my-page` | 내 잔액 / 작성물 / 구매물 |

---

## Slide 4 — ERD 설계 시 고려한 핵심 포인트 (5개)

이 5개가 "왜 이 모양인가"의 본질. 자세한 12개는 [`README.md`](../README.md) 참조.

1. **크레딧 원장 분리** — `user_credits`(잔액 캐시) + `credit_transactions`(append-only 원장). 단일 진실 공급원은 원장. 정정은 ADJUST 새 row.
2. **잔액 변경은 한 트랜잭션 + 비관적 락** — `SELECT FOR UPDATE`로 직렬화. 잔액·원장·권한 부여 4단계가 한 단위.
3. **아이디어 버저닝** — 메타(`ideas`)와 본문(`idea_documents`) 분리. 새 버전은 새 row, 구매자는 산 시점 스냅샷 보존하되 무료 업그레이드.
4. **`users.id = auth.users.id` UUID 공유** — Supabase Auth와 ID 일원화. JWT의 `sub`가 그대로 user_id.
5. **enum은 `varchar + CHECK`** — Postgres enum은 마이그레이션 비용이 너무 큼.

---

## Slide 5 — 제안하는 기술 스택

| 영역 | 선택 | 근거 |
|---|---|---|
| 백엔드 언어 | **Java 25 (LTS)** | 합의된 정통 스택 |
| 프레임워크 | **Spring Boot 3.5+** | Java 25 정식 지원, 생태계 |
| 빌드 | **Gradle Groovy DSL** | Java 표준, Kotlin DSL 미사용 |
| 프론트 | **Next.js 15+ (App Router)** | SSR + 클라이언트 인터랙션 |
| UI | **TailwindCSS + shadcn/ui** | 디자인 토큰·접근성 |
| DB·Auth·Storage | **Supabase Postgres** | 단일 DB, RLS, Realtime |
| 마이그레이션 | **Flyway 단일 도구** | Supabase Studio 직접 변경 금지 |
| LLM (대화) | **Claude Haiku 4.5** | 한국어 정서·저렴 |
| LLM (finalize) | **Claude Sonnet 4.6** | 구조화 출력 안정 |
| 임베딩 | **OpenAI text-embedding-3-small** | 1536D, $0.02/1M |
| 결제 | **MVP는 무료 크레딧** | 추후 PortOne |

---

## Slide 6 — 모노레포 구조

```
seedo/
├── README.md / CLAUDE.md / docs/
├── .github/workflows/   ← backend-ci, web-ci, lint
├── backend/             ← Spring Boot (dev.seedo)
│   ├── build.gradle (Groovy)
│   ├── src/main/java/dev/seedo/
│   │   ├── SeedoApplication.java
│   │   ├── auth/ credit/ idea/ project/ reward/ post/
│   │   └── ai/ search/ admin/ common/ config/
│   └── src/main/resources/db/migration/   ← Flyway V1, V2, ...
├── web/                 ← Next.js (App Router)
│   └── src/app/ components/ lib/ hooks/ types/
├── supabase/            ← (선택) RLS 정책 보조 SQL
└── infra/               ← (선택) docker-compose, Dockerfile
```

→ 단일 레포에서 backend/web/docs 함께 관리. CI는 경로 필터로 분리 실행.

---

## Slide 7 — Spring vs Supabase 책임 분담

**원칙**: 트랜잭션·외부 API·복잡 권한 → Spring. 단순 CRUD·RLS로 충분 → Supabase 직결.

**Spring이 처리** (트랜잭션 경계가 있는 것)
- 크레딧 충전·환불, 아이디어 구매, 채택→프로젝트+보상, 챗봇 LLM 호출, 자연어 검색, 회원 탈퇴, 관리자 액션

**Supabase 직결** (Next.js → supabase-js)
- 로그인·프로필·잔액 조회·피드·댓글·Hype/Follow·게시판 CRUD·모집 지원

전체 표: [`03_RESPONSIBILITY_SPLIT.md`](03_RESPONSIBILITY_SPLIT.md)

---

## Slide 8 — 합의 필요한 정책 (논의 안건)

기술이 아니라 **비즈/정책 결정**. 이 자리에서 정하면 좋음. 권장안 = 발제자 의견.

| 항목 | 옵션 | 권장 |
|---|---|---|
| Hype | 1회성 vs 토글 | **토글** (사용자 후회 가능) |
| 한 아이디어 → 여러 프로젝트 | 허용 vs 1:1 | **허용**, 보상은 첫 채택자만 |
| 아이디어 가격 | 작성자 자율 vs 플랫폼 고정 | **MVP: 플랫폼 고정 (10 크레딧)** |
| 채택 보상 | 자율 vs 비율 고정 | **가격의 50%** |
| 자가 채택 | 가능 vs 차단 | **가능, 보상 skip** |
| 아이디어 hard delete | 허용 vs 소프트만 | **소프트만**, ADMIN도 보상 정산 끝난 것만 |
| 알림 채널 (MVP) | 인앱 / 이메일 / 푸시 | **인앱 + Supabase Realtime** |
| 결제 PG | PortOne / Stripe / 안 씀 | **MVP: 무료 크레딧, 추후 PortOne** |

상세 근거: [`04_OPEN_QUESTIONS.md`](04_OPEN_QUESTIONS.md)

---

## Slide 9 — 첫 스프린트 작업 후보 (Phase 1)

합의 직후 시작할 수 있는 것들. 1~2주 단위.

1. **Supabase 프로젝트 생성** + vector / pgcrypto 확장 활성화
2. **Spring Boot 부트스트랩** (Java 25, Gradle Groovy, `dev.seedo` 패키지)
3. **Flyway V1 마이그레이션** — RBAC + users + user_credits + credit_transactions
4. **Supabase JWT 검증 필터** (`oauth2-resource-server` JwtDecoder + JWKS)
5. **회원가입 트리거** (`handle_new_user()` — Supabase 표준 패턴)
6. **Next.js 부트스트랩** + supabase-js 연동 + 로그인/가입 화면

이후 Phase 2: 크레딧 충전 + 아이디어 챗봇 + 임베딩 검색 + 구매.
전체 로드맵: [`03_RESPONSIBILITY_SPLIT.md`](03_RESPONSIBILITY_SPLIT.md) §구현 순서 제안.

---

## Slide 10 — 지금 합의해야 할 것 / 다음 단계

**오늘 미팅에서 결정**
- [ ] 기술 스택 동의? (Slide 5)
- [ ] 모노레포 구조 동의? (Slide 6)
- [ ] 책임 분담 원칙 동의? (Slide 7)
- [ ] 8개 정책 권장안 동의? (Slide 8)
- [ ] 첫 스프린트 6개 작업 우선순위·담당 분배 (Slide 9)

**미팅 후 처리**
- [ ] Supabase 프로젝트 생성 (담당:        )
- [ ] GitHub 레포 권한·브랜치 보호 규칙 (담당:        )
- [ ] CI 워크플로 작성 (담당:        )
- [ ] 회의록 작성 + 미결 안건 `docs/04_OPEN_QUESTIONS.md` 업데이트

**참고 문서**
- [`README.md`](../README.md) — 온보딩 (이 자리에서 화면 공유 권장)
- [`docs/00_PROJECT_CONTEXT.md`](00_PROJECT_CONTEXT.md) — 전체 컨텍스트
- [`docs/01_DB_SCHEMA.md`](01_DB_SCHEMA.md) — 18개 테이블 명세
- [`docs/02_BUSINESS_LOGIC.md`](02_BUSINESS_LOGIC.md) — 트랜잭션 의사 SQL
