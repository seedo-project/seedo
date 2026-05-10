# Seedo — Claude Code 작업 가이드

> 매 세션 자동 로드. AI 작업에 필요한 모든 결정·규칙·패턴이 여기 있다.
> `docs/`는 사람용 설계문서로만 두며 AI는 참조하지 않는다 (필요 시 사용자 명시 요청).

---

## 1. 한 줄 요약

**Seedo**는 일상 속 니즈를 가진 사용자(소비자)와 프로덕트 개발자를 잇는 **Pain Point 마켓플레이스 + 프로젝트 크라우드펀딩** 플랫폼. 사용자는 AI 챗봇과 대화하며 니즈를 정형화된 기획문서로 만들고, 개발자는 크레딧을 지불하고 열람·채택한다. 채택 시 작성자에게 보상이 가고, 아이디어는 프로젝트로 전환된다.

## 2. 모노레포 레이아웃

```
seedo/
├── README.md, CLAUDE.md           ← 온보딩 + AI 가이드
├── docs/                          ← 설계문서 (사람용)
├── backend/                       ← Spring Boot 3.5 — Java 25 + Gradle Groovy DSL (부트스트랩 완료)
│   └── CLAUDE.md
└── web/                           ← Next.js 15+ App Router (미구현)
    └── CLAUDE.md
```

`backend/`는 부트스트랩 완료 (Spring Boot 3.5.14, Gradle 8.14.4 wrapper, Flyway). `web/`은 폴더 + `CLAUDE.md`만 있는 상태.

## 3. 도메인 모델

```
User ──작성──▶ Idea ──채택──▶ Project
  │             │              ├─ Members / Followers / Hypes
  │             │              └─ Posts (홍보·모집)
  │             ├─ Documents (버전별 본문)
  │             ├─ ChatSessions (AI 대화 로그)
  │             ├─ Embeddings (검색용)
  │             └─ Purchases (열람권)
  ├─ Posts ──── Applications (베타테스터/개발자 모집)
  ├─ Credits (잔액 + 원장)
  └─ Roles (RBAC)
```

---

## 4. 아키텍처 원칙

1. **모놀리스 분담**: Next.js (FE) ↔ Spring Boot (핵심 비즈니스) + Supabase (Auth/CRUD/Storage/RLS).
2. **단일 DB**: Supabase Postgres를 두 백엔드가 공유.
3. **마이그레이션 일원화**: Flyway만. Supabase Studio 직접 스키마 변경 금지.
4. **Spring은 service_role 키**로 DB 접근 → RLS 우회, 권한은 `@PreAuthorize`에서 검증.
5. **JWT 검증**: Supabase 발급 JWT를 Spring Security `oauth2-resource-server` (JwtDecoder + JWKS)로 검증. 동일 JWT가 양쪽에서 통한다.
6. **소프트 삭제 우선**: `deleted_at`. hard delete는 ADMIN만, FK는 `RESTRICT`.
7. **USERS 동기화**: `users.id = auth.users.id` (UUID 공유), Supabase 트리거 `handle_new_user()`로 자동 생성.

---

## 5. 스키마 개요 (전체 DDL은 Flyway 마이그레이션이 캐노니컬)

PK는 별도 명시 없으면 `bigserial`. 시간 컬럼은 모두 `timestamptz`.

### 5.1 인증·권한 (RBAC)
- `users` (PK `uuid` = `auth.users.id`) — email, nickname, status
- `roles` (PK `serial`) — code, level. 시드: USER(1), ADMIN(2)
- `permissions` (PK `serial`) — code, resource, action. 시드 14개 (IDEA_CREATE 등)
- `user_roles`, `role_permissions` — 각각 UNIQUE 복합

### 5.2 크레딧
- `user_credits` (PK `user_id`) — balance, CHECK(balance ≥ 0). **잔액 캐시**
- `credit_transactions` — user_id, amount, type, balance_after. **append-only** (UPDATE/DELETE 트리거 차단). type ∈ {CHARGE, SPEND, REWARD, REFUND, ADJUST}

### 5.3 아이디어 (V2 예정)
- `ideas` — author_id, status, current_version_id, price_credits, reward_credits. status ∈ {DRAFT, PUBLISHED, ARCHIVED, DELETED}
- `idea_documents` — idea_id, version, content_md. UNIQUE(idea_id, version)
- `idea_chat_sessions`, `idea_chat_messages` — session.status ∈ {IN_PROGRESS, FINALIZED, ABANDONED}, msg.role ∈ {USER, ASSISTANT, SYSTEM}
- `idea_embeddings` (PK `idea_id`) — vector(1536), keywords. pgvector ivfflat + GIN
- `idea_purchases` — idea_id, document_id(산 시점 스냅샷), buyer_id, transaction_id. UNIQUE(idea_id, buyer_id)

### 5.4 프로젝트 (V3+ 예정)
- `projects` — idea_id, leader_id, status, idea_snapshot_md. status ∈ {DRAFT, RECRUITING, IN_PROGRESS, COMPLETED, ARCHIVED}
- `project_members` — partial UNIQUE(project_id, user_id) WHERE left_at IS NULL
- `project_follows` (PK 복합) — user_id, project_id

### 5.5 인터랙션
- `hypes` — user_id + (idea_id XOR project_id), partial UNIQUE 두 개
- `rewards` — recipient_user_id, reward_type, amount, status, transaction_id. type ∈ {ADOPTION, INTERVIEW, ADMIN, OTHER}

### 5.6 게시판
- `posts` — author_id, project_id, post_type ∈ {FREE, PROMO, BETA_RECRUIT, DEV_RECRUIT}, status
- `post_applications` — UNIQUE(post_id, applicant_id)

### 5.7 댓글 / 알림
- `idea_comments` / `project_comments` / `post_comments` — 동일 스키마 3테이블, Spring `@MappedSuperclass`로 추출
- `notifications` — type, reference_type/id, payload jsonb, is_read (MVP 미구현)

### 5.8 FK ON DELETE 정책
- 사용자 본인 작성물: 소프트 삭제. hard delete는 `RESTRICT`로 막힘
- 좋아요·댓글: `CASCADE`
- 아이디어 hard delete: `RESTRICT`. 보상 정산 끝난 것만 ADMIN 수동
- 프로젝트→멤버, 게시물→댓글·지원: `CASCADE`
- `credit_transactions`: 절대 삭제 금지. 정정은 ADJUST 새 행

---

## 6. 불변 규칙 (어기지 말 것)

1. **`credit_transactions` append-only** — V1 트리거가 UPDATE/DELETE 차단. 정정은 `type='ADJUST'` 새 행.
2. **잔액 + 원장 + 권한 부여는 항상 같은 트랜잭션** — `SELECT FOR UPDATE`로 직렬화.
3. **`users.id = auth.users.id`** — `auth.users` FK와 `handle_new_user()` 트리거는 Supabase 전용 별도 마이그레이션 (V1 제외).
4. **소프트 삭제 우선** — `status='DELETED'` + `deleted_at`. hard delete는 ADMIN만.
5. **published 후 수정해도 새 버전 row 생성** — 기존 보존(분쟁 방지). 구매자는 `idea_id` 기준 최신 무료 접근, `idea_purchases.document_id`는 산 시점 스냅샷.
6. **본인 아이디어는 못 산다** — author_id == buyer_id 차단.
7. **PG webhook 멱등성** — `(reference_type='PG_PAYMENT', reference_id=payment_id)` 사전 SELECT로 중복 차단.
8. **모더레이션·관리자 작업은 RBAC 검증 필수** — `@PreAuthorize("hasAuthority('PERM_<CODE>')")`.

---

## 7. Spring vs Supabase 책임 분담

**원칙**: 트랜잭션·외부 API·복잡 권한 → Spring. 단순 CRUD·RLS로 충분 → Supabase 직결.

**Spring**: 회원가입 후처리(트리거 백업), 크레딧 충전(PG webhook + 멱등성), 환불, 챗봇 LLM 호출, finalize, 새 버전 발행, 자연어 검색(RAG), 아이디어 구매, 채택→프로젝트+보상, 회원 탈퇴, 관리자 액션.

**Supabase 직결** (Next.js → supabase-js): 로그인/회원가입/비번 재설정, 프로필·잔액·거래내역 조회(RLS), 아이디어 피드/메타 수정, 본문 조회(RLS: purchases EXISTS), 프로젝트 피드/상세/가입 신청, Hype/Follow 토글, 댓글 CRUD, 게시물 CRUD/지원.

### RLS 정책 패턴
```sql
USING (user_id = auth.uid())                         -- 본인만
USING (author_id = auth.uid())                       -- 작성자 수정
USING (status = 'PUBLISHED' AND deleted_at IS NULL)  -- 공개 읽기
USING (EXISTS (SELECT 1 FROM idea_purchases          -- 구매자 본문
       WHERE idea_id=... AND buyer_id=auth.uid())
    OR EXISTS (SELECT 1 FROM ideas
       WHERE id=... AND author_id=auth.uid()))
```

`service_role` 키는 RLS 우회. Spring은 이 키 → 권한은 코드에서 검증.

---

## 8. 핵심 트랜잭션 패턴

각 패턴: `@Transactional(isolation=READ_COMMITTED)` + `SELECT FOR UPDATE`.

### 8.1 크레딧 충전 (PG webhook)
1. 멱등성 체크 `(PG_PAYMENT, payment_id)` → 있으면 skip
2. `user_credits` FOR UPDATE → balance 갱신
3. `credit_transactions` INSERT (CHARGE, balance_after)

### 8.2 아이디어 구매
1. `idea_purchases (idea_id, buyer_id)` 중복 체크 → 있으면 "이미 구매"
2. `ideas` FOR UPDATE → PUBLISHED & author_id ≠ buyer_id 검증
3. `user_credits` FOR UPDATE → balance ≥ price 검증
4. balance 차감 + `credit_transactions` INSERT (SPEND, -price)
5. `idea_purchases` INSERT (document_id = current_version_id 스냅샷)

### 8.3 채택 → 프로젝트 + 보상 (가장 복잡)
1. `ideas` FOR UPDATE → PUBLISHED 검증
2. 채택자가 산 적 있는지 확인 (정책: 산 사람만 채택)
3. `projects` INSERT (idea_snapshot_md = idea_documents.content_md 복사)
4. `project_members` INSERT (LEADER, ACTIVE)
5. 작성자 `user_credits` FOR UPDATE → balance += reward_credits
6. `credit_transactions` INSERT (REWARD)
7. `rewards` INSERT (PAID, transaction_id, paid_at=now())

> MVP는 한 트랜잭션. 트래픽 늘면 `@TransactionalEventListener(AFTER_COMMIT)`로 보상 비동기화.

### 8.4 챗봇 finalize / 새 버전 발행
- **finalize**: `ideas` INSERT(DRAFT) → `idea_documents` INSERT(version=1) → `ideas.current_version_id` UPDATE → `idea_chat_sessions` UPDATE(FINALIZED)
- **새 버전**: 본인 검증 → `MAX(version)+1` → `idea_documents` INSERT → `ideas.current_version_id` UPDATE
- 둘 다 트랜잭션 외에서 비동기 임베딩 (재)추출 → `idea_embeddings` upsert.

### 8.5 신규 API 체크리스트
- [ ] 잔액 변경이면 `user_credits` 락 + `credit_transactions` 같은 트랜잭션
- [ ] UNIQUE 위반 → 비즈니스 에러로 변환
- [ ] 외부 API(LLM/PG)는 짧은 타임아웃 + 보상 트랜잭션
- [ ] webhook은 멱등성 키 보호
- [ ] 권한 체크가 비즈니스 로직 진입 전
- [ ] 동시성 락 전략 명시 (낙관적 vs 비관적)

---

## 9. RBAC & 인증

**시스템 권한**: `@PreAuthorize("hasAuthority('PERM_CREDIT_REFUND')")`. 권한 로딩은 `users → user_roles → role_permissions → permissions` 조인 → `SimpleGrantedAuthority("PERM_" + code)`. Caffeine TTL 5분.

**리소스 소유권 (RBAC 외부)**: `@PreAuthorize("@ideaSecurity.isOwner(#ideaId, authentication)")`. `@Component("ideaSecurity")` 빈에서 `existsByIdAndAuthorId` 체크.

**JWT 검증 필터**: Authorization Bearer → JWKS 공개키 fetch (`https://<proj>.supabase.co/auth/v1/.well-known/jwks.json`, 캐싱) → 서명·exp 검증 → `sub` claim → user_id → 권한 로드 → SecurityContext set. `OncePerRequestFilter` 상속, `nimbus-jose-jwt`(`oauth2-resource-server`에 포함).

---

## 10. 코드 컨벤션

### 10.1 명명
- 테이블 `snake_case` 복수형 / PK `id` (대부분 bigserial, `users`만 uuid) / FK `<단수>_id` / 시간 `*_at` (`timestamptz`) / Boolean `is_*` 또는 `*_open`
- Java 패키지 루트: `dev.seedo`, Application: `SeedoApplication`

### 10.2 enum / 마이그레이션
- Postgres enum **금지**. `varchar + CHECK (status IN (...))` 사용.
- 경로: `backend/src/main/resources/db/migration/V<N>__<snake_case>.sql`
- 적용된 마이그레이션 **수정 금지** — 항상 새 V번호로 추가
- JPA `ddl-auto: validate` 유지 (`update`/`create` 금지)

### 10.3 Spring
- **스택**: Java 25 + Spring Boot 3.5+ + Gradle Groovy DSL (`build.gradle`/`settings.gradle`). Kotlin·Kotlin DSL 안 씀.
- 트랜잭션 `@Transactional(isolation=READ_COMMITTED)` + 비관적 락 명시
- 외부 호출: WebClient + Resilience4j (서킷 5회/60초 OPEN, 일시 오류만 3회 재시도)
- 캐시: Caffeine (권한 TTL 5분)
- 댓글: `@MappedSuperclass BaseComment` → `IdeaComment`/`ProjectComment`/`PostComment`

### 10.4 Next.js
- **스택**: Next.js 15+ (App Router) + TS strict + TailwindCSS + shadcn/ui
- Server Component 기본, `"use client"`는 인터랙션만
- 데이터 페칭: Server Component에서 supabase server client 또는 Spring API
- Supabase 클라이언트는 `@supabase/ssr` (`auth-helpers-nextjs`는 deprecated)
- 단순 조회는 supabase-js 직결, 트랜잭션은 Spring API 경유

---

## 11. 결정된 기술 스택

| 영역 | 선택 |
|---|---|
| 백엔드 | **Java 25 (LTS)** + **Spring Boot 3.5+** + **Gradle Groovy DSL** (`build.gradle`). Kotlin/Kotlin DSL 미사용. 패키지 `dev.seedo`, Application `SeedoApplication` |
| 보안 | Spring Security + `oauth2-resource-server` (JWKS) |
| 프론트 | **Next.js 15+ (App Router)** + TS strict + TailwindCSS + shadcn/ui + `@supabase/ssr` |
| DB / Auth / Storage / Realtime | **Supabase Postgres** (단일 DB, RLS) |
| 마이그레이션 | **Flyway** (`flyway-core` + `flyway-database-postgresql`). Supabase Studio 직접 변경 금지 |
| LLM 대화 | Claude Haiku 4.5 (`claude-haiku-4-5`) — $0.25/$1.25, 한국어 정서 양호 |
| LLM finalize | Claude Sonnet 4.6 (`claude-sonnet-4-6`) — 구조화 출력 |
| 임베딩 / 벡터 | OpenAI text-embedding-3-small (1536D, $0.02/1M) + pgvector (ivfflat/hnsw). 모델 변경 = 전체 재인덱싱 |
| 결제 | MVP는 무료 크레딧, 추후 PortOne |
| 마크다운 / 메트릭 | TipTap / PostHog (권장) |

API 키 환경변수: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `SUPABASE_*`.

---

## 12. 미결정 사항 — 권장 옵션으로 진행하되 한 줄 확인

| 주제 | 권장 |
|---|---|
| Hype 1회성 vs 토글 | 토글 |
| 한 아이디어 → 여러 프로젝트 | 허용, 보상은 첫 채택자만 |
| 아이디어 가격 | MVP: 플랫폼 고정가 (10 크레딧) |
| 채택 보상 | 가격의 50% |
| 자가 채택 | 가능, 보상 skip |
| 아이디어 hard delete | 작성자 archive만, ADMIN도 정산 끝난 것만 |
| 알림 | MVP: 인앱 + Supabase Realtime |
| 챗봇 스트리밍 | SSE |

---

## 13. 자주 쓰는 명령

> `backend/`, `web/`은 폴더 + `CLAUDE.md`만 있는 상태. 아래는 **부트스트랩 완료 후** 사용.

```sh
# backend (Java 25 + Spring Boot)
(cd backend && ./gradlew bootRun)              # 실행
(cd backend && ./gradlew flywayInfo)           # 마이그레이션 상태
(cd backend && ./gradlew flywayMigrate)        # 수동 마이그레이션
(cd backend && ./gradlew test)                 # 테스트
ls backend/src/main/resources/db/migration/    # 다음 V번호

# web (Next.js)
(cd web && npm run dev)                        # 개발 서버
(cd web && npm run build && npm start)         # 프로덕션

# 환경 변수 (.env 또는 IDE run config)
SUPABASE_DB_URL              # jdbc:postgresql://db.<proj>.supabase.co:5432/postgres
SUPABASE_DB_USER, SUPABASE_DB_PASSWORD
SUPABASE_JWKS_URL            # https://<proj>.supabase.co/auth/v1/.well-known/jwks.json
SUPABASE_JWT_ISSUER          # https://<proj>.supabase.co/auth/v1
SUPABASE_SERVICE_ROLE_KEY
ANTHROPIC_API_KEY, OPENAI_API_KEY
```

---

## 14. 진행 상태 (자주 stale — 시작점만)

- **현재**: 도메인/ERD 설계 완료 (`docs/`), `backend/` Flyway V1+V2 + JPA 엔티티(User·RBAC·크레딧·idea 전체) + 상태 전이 가드 + DDD 4 레이어 + V1·V2 invariant IT + Supabase JWT 인증/RBAC 권한 로딩 필터 (#64) 까지. idea Service/Controller, 크레딧 Service, `handle_new_user()` 트리거는 0줄. `web/`는 디자인 페이지들이 main 에 진입.
- 다음 작업: `handle_new_user()` Supabase 트리거 → 크레딧 충전 (PG webhook + 멱등성, §8.1) → 챗봇 finalize / 아이디어 새 버전 발행 (§8.4) → 아이디어 구매 (§8.2).
- 전체 순서: 인프라/인증 → 크레딧/아이디어 → 프로젝트/보상 → 게시판/인터랙션 → 알림/관리자/배포

> 컨벤션·트랜잭션 패턴(§6~§10)은 합의 후에도 유효.

---

## 15. 협업 및 Git 컨벤션

팀원과 협업 시 코드의 이력을 명확히 관리하기 위해 아래 규칙을 준수한다.

### 15.1 이슈 (Issue) 관리
작업 시작 전 항상 이슈를 먼저 생성한다.
- **제목**: `[유형] 작업 내용` (예: `[Feat] 로그인 API 구현`)
- **라벨**: `feature`, `bug`, `documentation`, `refactor` 등 적절한 라벨 부여

### 15.2 브랜치 전략 (GitHub Flow)
이슈 번호를 포함하여 어떤 작업을 하는 브랜치인지 명시한다.
- **형식**: `유형/#이슈번호-간략내용`
- **예시**:
    - `feat/#12-login-api`
    - `fix/#7-header-zindex`
    - `docs/#3-readme-update`

### 15.3 커밋 메시지 (Conventional Commits)
커밋 메시지 끝에 관련 이슈 번호를 명시하여 추적성을 높인다.
- **형식**: `유형: 설명 (#이슈번호)`
- **주요 유형**:
    - `feat`: 새로운 기능 추가
    - `fix`: 버그 수정
    - `docs`: 문서 수정 (README, CLAUDE.md 등)
    - `style`: 코드 포맷팅 (코드 로직 변경 없음)
    - `refactor`: 코드 리팩토링
    - `chore`: 빌드 설정, 패키지 매니저 관리 등
- **예시**: `feat: 이메일 유효성 검사 추가 (#12)`

### 15.4 Pull Request (PR) 및 병합
- **리뷰 필수**: 최소 1명 이상의 승인이 있어야 `main` 브랜치에 병합 가능.
- **이슈 연결**: PR 설명에 `Closes #이슈번호`를 적어 병합 시 자동으로 이슈가 닫히게 한다.
- **병합 방식**: Squash and Merge 권장 (히스토리 간소화).
