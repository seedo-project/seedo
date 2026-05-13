# 07. 구현 상태 (Supabase / Spring 분담 실제 매핑)

> `03_RESPONSIBILITY_SPLIT.md` 가 **설계 원칙** 이라면, 이 문서는 **현재 실제로 머지된 것** 이다.
> 두 문서를 같이 읽으면 \"무엇을 어떻게 만들기로 했고, 지금 어디까지 들어왔는지\" 가 한 번에 잡힌다.
>
> 갱신 시점: 2026-05-13 — Flyway V1~V18 머지된 상태 기준.

---

## 한 줄 요약

이코노미 루프 (작성 → 검색 → 구매 → 채택 → 보상) + 프로젝트 표지 + 게시판 + 좋아요/스크랩/팔로우 + 보안 RLS 까지 **핵심 backend critical path 완성**. 알림은 보류, 모집·지원 흐름은 게시판으로 흡수, PortOne 실연동은 MVP 외.

---

## 🗄️ Supabase 측 — 데이터 + 정책 + 직결

### 테이블 (Flyway V1~V18)

| 영역 | 테이블 | 무엇 |
|---|---|---|
| **인증·권한** | `users` / `roles` / `permissions` / `user_roles` / `role_permissions` | 사용자 + RBAC 시스템 |
| | `auth.users` ↔ `public.users` 동기 트리거 (`handle_new_user`) | Supabase 가입 시 자동 생성 |
| **크레딧** | `user_credits` (잔액 캐시) | balance >= 0 CHECK |
| | `credit_transactions` (원장, append-only) | UPDATE/DELETE 차단 트리거 |
| **아이디어** | `ideas` | status ∈ {DRAFT, PUBLISHED, ARCHIVED, DELETED} |
| | `idea_documents` (버전별 본문) | UNIQUE(idea_id, version), 분쟁 방지 보존 |
| | `idea_embeddings` (vector(1536) + keywords text[]) | pgvector ivfflat + GIN(keywords) |
| | `idea_chat_sessions` / `idea_chat_messages` | finalize 전 챗봇 로그 |
| | `idea_purchases` (document_id 스냅샷) | UNIQUE(idea_id, buyer_id) |
| **프로젝트** | `projects` (V15 — cover_image_url, title, description, guide_md 4 항목 + idea_snapshot_md 백업) | LEADER 작성 표지 |
| | `project_members` | partial UNIQUE(project_id, user_id) WHERE left_at IS NULL |
| | `project_scraps` (V15) | 북마크. PK 복합 (user_id, project_id) |
| | `project_follows` (V18) | 업데이트 구독. PK 복합 |
| **보상** | `rewards` | partial UNIQUE(idea_id) WHERE reward_type='ADOPTION' |
| **상호작용** | `hypes` (V12) | 좋아요. user_id + (idea_id XOR project_id) |
| | `idea_comments` / `project_comments` / `post_comments` (V13, V14) | 댓글. @MappedSuperclass BaseComment |
| **게시판** | `posts` (V14) | post_type ∈ {FREE, PROMO, BETA_RECRUIT, DEV_RECRUIT} |

### RLS 정책

| 패턴 | 적용 테이블 |
|---|---|
| **본인 row 만** (`USING (user_id = auth.uid())`) | `user_credits`, `credit_transactions`, `hypes` (V16), `project_scraps` (V16), `users` profile |
| **공개 SELECT** (PUBLISHED + not deleted) | `ideas`, `posts`, 각종 댓글, `projects` (status 별), `idea_documents` (메타) |
| **공개 SELECT** (전체) | `project_follows` (V18) — \"⚠️ V12/V15 와 같은 hole 패턴\" — V16 lesson 반영 안 됨, follow-up 후보 |
| **작성자만 UPDATE/DELETE** (`USING (author_id = auth.uid())`) | `ideas` 메타, `posts`, 각종 댓글 |
| **본문 열람 가드** (`EXISTS idea_purchases ... OR author`) | `idea_documents` content |

### Postgres View — V16 신설

| View | 무엇 |
|---|---|
| `idea_hype_counts(idea_id, count)` | 아이디어별 좋아요 개수 |
| `project_hype_counts(project_id, count)` | 프로젝트별 좋아요 개수 |
| `project_scrap_counts(project_id, count)` | 프로젝트별 스크랩 개수 |
| `public_profiles` (V7, V11) | 닉네임·이미지 등 공개 프로필 |

→ FE 가 카운트 노출에 사용, owner 정보는 새지 않음.

### Trigger / Function

- `set_updated_at()` — 모든 테이블의 `updated_at` 자동 갱신
- `handle_new_user()` (V3, V8, V10) — `auth.users` INSERT → `public.users` + `user_credits` + `user_roles(USER)` 동시 생성
- `block_credit_tx_modification()` (V1) — `credit_transactions` UPDATE/DELETE 차단

### Supabase 가 자체 제공 (FE 직결)

- **Auth**: 이메일·SNS 로그인 / JWT 발급
- **Realtime**: 테이블 변화 push 구독
- **Storage**: 대표 이미지·프로필 이미지 보관
- **Database REST**: RLS 가 자동 가드하는 supabase-js 직결 쿼리

### FE 가 Spring 거치지 않는 흐름

| 흐름 | 직결 가능 이유 |
|---|---|
| 로그인·회원가입·비밀번호 재설정 | Supabase Auth |
| 프로필·잔액·거래내역 조회 | RLS 본인 row 만 |
| 아이디어 / 프로젝트 / 게시물 피드·상세 조회 | RLS public SELECT |
| 본문 조회 (구매자/작성자) | RLS EXISTS 가드 |
| 좋아요·스크랩·팔로우 토글 | RLS self_insert/self_delete |
| 댓글 CRUD | RLS author_id |
| 카운트 조회 | V16 view |
| 대표 이미지 업로드 | Supabase Storage 직결 |

---

## ☕ Spring Boot 측 — 트랜잭션 + 외부 API + 복잡 권한

### 도메인별 패키지 구조

```text
dev.seedo.{auth/credit/user/idea/project/reward/post}
├── domain/         JPA 엔티티 (aggregate root)
├── application/    @Service @Transactional
├── infrastructure/ JpaRepository / 외부 API 어댑터
└── web/            @RestController + DTO + ExceptionHandler
```

### 처리하는 트랜잭션·API

| 흐름 | 서비스 | API | 왜 Spring? |
|---|---|---|---|
| 챗봇 응답 한 턴 | `SendChatMessageService` | `POST /chat/sessions/{id}/messages` | OpenAI 호출 + DB 저장 |
| **챗봇 finalize → 자동 PUBLISHED** | `FinalizeChatSessionService` | `POST /chat/sessions/{id}/finalize` | LLM 합성 + idea + document + publish + 이벤트 (§8.4) |
| 새 버전 발행 | `PublishIdeaVersionService` | `POST /ideas/{id}/versions` | 새 row INSERT + embedding 재추출 이벤트 |
| 아이디어 구매 | `PurchaseIdeaService` | `POST /ideas/{id}/purchase` | 잔액 락 + 원장 + 본문 열람권 (§8.2) |
| 하이브리드 검색 | `SearchIdeasService` | `GET /ideas/search` | OpenAI 임베딩 + RRF + fallback |
| 아이디어 채택 | `AdoptIdeaService` | `POST /ideas/{id}/adopt` | 프로젝트 + 멤버 + 보상 (§8.3) |
| 크레딧 충전 webhook | `ChargeCreditService` + `PaymentWebhookController` | `POST /webhooks/payments/portone` | 잔액 + 원장 + 멱등성 (§8.1) |
| 관리자 크레딧 적립 | `AdminCreditController` + `ChargeCreditService` | `POST /admin/credit/grant` | RBAC + ADJUST 원장 |
| 프로젝트 소개 작성 | `UpdateProjectIntroService` | `PATCH /projects/{id}/intro` | LEADER 권한 + 상태 가드 |
| 프로젝트 공개 전이 | `PublishProjectService` | `POST /projects/{id}/publish` | LEADER + DRAFT → IN_PROGRESS + 필수 필드 |

### 외부 통합 어댑터 (헥사고날 — `application/port/out/` + `infrastructure/openai/`)

| 어댑터 | 모델 | 용도 |
|---|---|---|
| `OpenAiChatAdapter` | `gpt-4o-mini` | 챗봇 응답 + finalize 합성 (JSON mode) |
| `OpenAiEmbeddingAdapter` | `text-embedding-3-small` (1536D) | 검색·인덱싱 임베딩 |

- Resilience4j 서킷 5회/60초 OPEN, 일시 오류 3회 재시도
- WebClient + responseTimeout
- 테스트는 `IntegrationTestStubsConfig` 가 stub 빈 주입

### 인증·권한

- **JWT 검증**: Supabase 발급 JWT → Spring `JwtDecoder` (JWKS) 로 서명·exp·issuer 검증
- **RBAC**: `@PreAuthorize("hasAuthority('PERM_xxx')")` — 시스템 권한 (14개 PERM_*)
- **리소스 소유권**: 도메인별 service 에서 `leader_id`/`author_id`/`user_id` 비교 + 예외
- **권한 캐시**: Caffeine TTL 5분
- **`@CurrentUserId UUID`** 파라미터 어노테이션으로 JWT sub 자동 주입

### 이벤트 / 비동기

- `IdeaVersionPublishedEvent` → `IdeaEmbeddingRefreshListener` (AFTER_COMMIT, REQUIRES_NEW) — 비동기 임베딩 추출 + upsert

### API 봉투

- 모든 응답이 `ApiResponse<T>` 봉투로 자동 감싸짐 (`ApiResponseAdvice`)
- 에러는 도메인별 `*ExceptionHandler` 가 `ApiResponse.error(message)` 로 직접 반환
- 4xx/5xx 매핑: 404 (`NotFound*`), 400 (validation·도메인 거부), 403 (소유권), 409 (상태 충돌), 401 (JWT 없음)

---

## 🔗 양쪽 다 관련된 것

- **JWT**: Supabase 발급 ↔ Spring JWKS 검증. 같은 토큰이 양쪽 통과
- **단일 DB**: 같은 Postgres. Spring 은 `service_role` 키로 RLS 우회 + 코드 권한, Supabase 직결은 RLS 가드
- **Flyway 가 유일한 스키마 변경 경로**: Supabase Studio 에서 직접 변경 금지

---

## 📦 Flyway 마이그레이션 인덱스

| V | 무엇 |
|---|---|
| V1 | init RBAC + 크레딧 (users, roles, user_credits, credit_transactions, append-only 트리거) |
| V2 | ideas + idea_documents + idea_chat_sessions/messages + idea_embeddings + idea_purchases |
| V3 | Supabase auth 동기 — auth.users FK + handle_new_user 트리거 |
| V4 | projects + project_members + rewards |
| V5 | project active LEADER + reward 트랜잭션 unique 가드 |
| V6 | RLS 정책 (ideas / idea_documents / projects / 등) |
| V7 | public_profiles view |
| V8 | handle_new_user 가 metadata 의 nickname 사용 |
| V9 | 사용자 프로필 보강 (이름·생년월일·성별·이미지) |
| V10 | handle_new_user profile metadata 전파 |
| V11 | public_profiles 에 이미지 포함 |
| V12 | hypes (좋아요) |
| V13 | idea_comments / project_comments |
| V14 | posts + post_comments |
| V15 | projects 4 항목 (cover/title/description/guide_md) + project_scraps |
| V16 | hypes/scraps RLS owner-only + 카운트 view |
| V17 | idea_embeddings.keywords lower-case backfill |
| V18 | project_follows (업데이트 구독) |

---

## 📚 머지된 PR 인덱스 (최근 → 과거)

| PR | 무엇 |
|---|---|
| #152 (V18) | 프로젝트 팔로우 토글 |
| #151 | **finalize 후 자동 PUBLISHED** (마지막 1cm) |
| #148 (V17) | 키워드 lower-case 정규화 (검색 정합) |
| #146 | 프로젝트 스크랩 UI 연결 + 댓글 post target 보강 (FE) |
| #143 (V16) | hypes/scraps RLS owner-only + 카운트 view |
| #141 (V15) | 프로젝트 소개 4 항목 + 스크랩 + publish |
| #139 (V13~) | 하이브리드 검색 RRF |
| #137 (V14) | 게시판 + 댓글 |
| #136 (V13) | 아이디어/프로젝트 댓글 |
| #134 / #133 | 챗봇 finalize 키워드 추출 정교화 |
| #128 / #127 | finalize LLM 자동 작성 |
| #126 / #125 | 챗봇 send |
| #114 / #109 | 자연어 검색 토대 |
| #83 | 임베딩 실제 계산 + ports/adapter 첫 적용 |
| #81 (V4) | 채택 트랜잭션 (§8.3) |
| #79 | finalize / 새 버전 발행 (§8.4) |
| #75 (V2) | 아이디어 / 구매 / RBAC 시드 |
| #66 / #65 / #64 | CI / Supabase 동기 / JWT 인증 |

---

## ⛔ 미구현 / 보류

| 항목 | 상태 |
|---|---|
| **알림 (notifications)** | git stash 에 코드 보관 (V18 notifications 테이블 + Notification 엔티티 + Listener + Controller 일부). \"FE 가 Realtime 직결\" vs \"Spring 발화\" 결정 보류 |
| **모집 / 지원 흐름** | 의도적으로 **안 만듦** — 게시판 모집글 (`BETA_RECRUIT` / `DEV_RECRUIT`) 작성 + 외부 채널 연락으로 흡수. `post_applications` 테이블도 없음 |
| **HMAC-SHA256 webhook 서명 검증** | MVP 외 (`X-Webhook-Secret` 평문 비교만). PortOne 실연동 전 토대 |
| **PortOne 실연동 어댑터** | MVP 외 — 무료 크레딧 (§12) |
| **환불 흐름** | MVP 외 — 결제 자체가 없음 |
| **RAG-G (검색 결과 한 줄 요약)** | #138 명시적 제외 |
| **한국어 형태소 분석기 (Nori)** | #138 명시적 제외 |
| **인기 쿼리 임베딩 캐싱** | 트래픽 늘면 |
| **`project_follows` RLS owner-only 좁힘** | V18 가 V12/V15 와 같은 `USING (true)` 패턴 사용 — V16 lesson 미반영. follow-up 후보 |
| **DRAFT 프로젝트 RLS 좁힘** | V15 메모만 있고 미구현 |
| **신고 / 모더레이션** | 운영자 SQL 처리 |
| **관리자 콘솔 / 대시보드** | MVP 외 |

---

## 🚀 MVP 출시 블로커 (backend 외부)

- **FE ↔ backend 연결** — `web/` 가 #146 으로 일부 시작 (스크랩·댓글). 채택·검색·프로젝트 작성·게시판 API 연동 대부분 미진행 (FE 담당자 영역)
- **배포 환경 / CI 운영 설정** — backend-test CI 만 있음
- **메트릭 (PostHog)** — 미연결

---

## 한 줄 비유

- **Supabase** = 도서관 사서. 책 정리·대출 카드·열람실 출입 통제·실시간 알림판·사진 보관소.
- **Spring** = 회계사 + 변호사. 돈 거래·복잡한 계약·외부 거래처 (OpenAI, PG) 와의 협상.

**90% 의 단순 조회·CRUD 는 Supabase 가 알아서 처리**, Spring 은 **잔액이 변하거나 외부 API 가 끼는 트랜잭션 + 복잡 권한** 만 받아서 처리.
