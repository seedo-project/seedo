# 01. DB 스키마 명세

> Postgres 16+ (Supabase) 기준. 모든 시간 컬럼은 `timestamptz`. 모든 PK는 별도 명시 없으면 자동 생성.

## 명명 규칙

- 테이블: `snake_case` 복수형 (`users`, `idea_documents`)
- PK: `id` (대부분 `bigserial`. `users`만 `uuid`)
- FK: `<참조테이블단수>_id` (예: `idea_id`, `user_id`)
- 시간 컬럼: `*_at` (`created_at`, `updated_at`, `deleted_at`)
- Boolean: `is_*` 또는 `*_open` 형태
- 모든 테이블에 `created_at` 기본 포함, 수정 가능 도메인은 `updated_at`도 포함

---

## 1. 인증 · 권한 (RBAC)

### 1.1 `users` (프로필)

```
id              uuid PK              -- Supabase auth.users.id 와 동일 값 사용
email           varchar(255) UNIQUE NOT NULL
nickname        varchar(50) NOT NULL
profile_url     text
real_name       varchar(50)          -- 회원가입 시 입력
birthday        date
gender          varchar(10)          -- 'M' / 'F' / 'OTHER' / NULL
status          varchar(20) NOT NULL DEFAULT 'ACTIVE'   -- ACTIVE / SUSPENDED / DELETED
deleted_at      timestamptz
created_at      timestamptz NOT NULL DEFAULT now()
updated_at      timestamptz NOT NULL DEFAULT now()
```

**제약/인덱스**
- `UNIQUE(email)`
- `INDEX(nickname)` — 검색용
- FK: `users.id` REFERENCES `auth.users(id)` ON DELETE CASCADE (확정)
- 동기화: DB 트리거 `handle_new_user()` 사용 — 자세한 내용은 `04_OPEN_QUESTIONS.md`의 B.1 결정사항 참조

### 1.2 `roles`

```
id           serial PK
code         varchar(30) UNIQUE NOT NULL    -- 'USER', 'ADMIN'
name         varchar(50) NOT NULL
level        int NOT NULL                   -- 1=USER, 10=ADMIN
description  text
created_at   timestamptz NOT NULL DEFAULT now()
```

**시드 데이터**
```
(1, 'USER',  '일반 사용자',     1)
(2, 'ADMIN', '관리자',          10)
```

### 1.3 `permissions`

```
id           serial PK
code         varchar(50) UNIQUE NOT NULL    -- 'IDEA_CREATE' 등
resource     varchar(30) NOT NULL           -- 'IDEA', 'PROJECT', 'CREDIT' 등
action       varchar(30) NOT NULL           -- 'CREATE', 'READ', 'MODERATE' 등
description  text
created_at   timestamptz NOT NULL DEFAULT now()
```

**시드 예시**
```
IDEA_CREATE, IDEA_PURCHASE, IDEA_MODERATE
PROJECT_CREATE, PROJECT_MODERATE
POST_CREATE, POST_MODERATE
COMMENT_CREATE, COMMENT_MODERATE
CREDIT_CHARGE, CREDIT_REFUND, CREDIT_ADMIN
USER_BAN, USER_VIEW_PRIVATE
```

### 1.4 `user_roles`

```
id           bigserial PK
user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE
role_id      int  NOT NULL REFERENCES roles(id) ON DELETE RESTRICT
assigned_by  uuid REFERENCES users(id)
assigned_at  timestamptz NOT NULL DEFAULT now()

UNIQUE (user_id, role_id)
```

### 1.5 `role_permissions`

```
id            bigserial PK
role_id       int NOT NULL REFERENCES roles(id) ON DELETE CASCADE
permission_id int NOT NULL REFERENCES permissions(id) ON DELETE CASCADE

UNIQUE (role_id, permission_id)
```

---

## 2. 크레딧

### 2.1 `user_credits`

잔액 캐시. 모든 변경은 `credit_transactions` 한 줄과 함께.

```
user_id     uuid PK REFERENCES users(id) ON DELETE CASCADE
balance     bigint NOT NULL DEFAULT 0   -- 음수 금지 CHECK
updated_at  timestamptz NOT NULL DEFAULT now()

CHECK (balance >= 0)
```

### 2.2 `credit_transactions` (원장)

모든 크레딧 이동 기록. **append-only**, UPDATE/DELETE 금지 (트리거로 강제).

```
id              bigserial PK
user_id         uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT
amount          bigint NOT NULL              -- 양수=증가, 음수=감소
type            varchar(20) NOT NULL         -- CHARGE / SPEND / REWARD / REFUND / ADJUST
reference_type  varchar(30)                  -- 'IDEA_PURCHASE', 'REWARD', 'PG_PAYMENT' 등
reference_id    bigint                       -- 참조 PK (FK는 polymorphic이라 안 검)
balance_after   bigint NOT NULL              -- 트랜잭션 후 잔액 (감사용)
memo            text
created_at      timestamptz NOT NULL DEFAULT now()

INDEX (user_id, created_at DESC)
INDEX (reference_type, reference_id)
```

---

## 3. 아이디어

### 3.1 `ideas` (메타)

```
id                    bigserial PK
author_id             uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT
title                 varchar(200) NOT NULL
summary               text                       -- 무료 미리보기 요약
category              varchar(50)
status                varchar(20) NOT NULL       -- DRAFT / PUBLISHED / ARCHIVED / DELETED
current_version_id    bigint REFERENCES idea_documents(id)  -- 발행된 최신 버전
price_credits         bigint NOT NULL DEFAULT 0  -- 열람 비용 (관리자 또는 작성자가 설정)
reward_credits        bigint NOT NULL DEFAULT 0  -- 채택 시 작성자 보상
hype_count            int NOT NULL DEFAULT 0     -- 비정규화 (트리거로 동기화 또는 제거 검토)
published_at          timestamptz
deleted_at            timestamptz
created_at            timestamptz NOT NULL DEFAULT now()
updated_at            timestamptz NOT NULL DEFAULT now()

INDEX (status, published_at DESC)         -- 피드 정렬
INDEX (author_id, created_at DESC)         -- 마이페이지
INDEX (category)
```

### 3.2 `idea_documents` (버전별 본문)

```
id              bigserial PK
idea_id         bigint NOT NULL REFERENCES ideas(id) ON DELETE CASCADE
version         int NOT NULL                 -- 1부터 증가
content_md      text NOT NULL
attachment_urls jsonb                        -- [{"url": "...", "name": "..."}]
created_by      uuid NOT NULL REFERENCES users(id)
change_note     text
created_at      timestamptz NOT NULL DEFAULT now()

UNIQUE (idea_id, version)
INDEX (idea_id, created_at DESC)
```

### 3.3 `idea_chat_sessions`

```
id            bigserial PK
user_id       uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE
idea_id       bigint REFERENCES ideas(id) ON DELETE SET NULL  -- 완료 시 idea 연결
status        varchar(20) NOT NULL  -- IN_PROGRESS / FINALIZED / ABANDONED
finalized_at  timestamptz
created_at    timestamptz NOT NULL DEFAULT now()

INDEX (user_id, created_at DESC)
INDEX (idea_id)
```

### 3.4 `idea_chat_messages`

```
id           bigserial PK
session_id   bigint NOT NULL REFERENCES idea_chat_sessions(id) ON DELETE CASCADE
role         varchar(15) NOT NULL  -- USER / ASSISTANT / SYSTEM
content      text NOT NULL
token_count  int
created_at   timestamptz NOT NULL DEFAULT now()

INDEX (session_id, created_at)
```

### 3.5 `idea_embeddings` (pgvector)

```
idea_id     bigint PK REFERENCES ideas(id) ON DELETE CASCADE
embedding   vector(1536) NOT NULL    -- OpenAI text-embedding-3-small 기준
keywords    text[] NOT NULL DEFAULT '{}'
updated_at  timestamptz NOT NULL DEFAULT now()

INDEX USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)
INDEX USING gin (keywords)
```

> 확장 활성화 필요: `CREATE EXTENSION IF NOT EXISTS vector;`

### 3.6 `idea_purchases` (열람권)

```
id              bigserial PK
idea_id         bigint NOT NULL REFERENCES ideas(id) ON DELETE RESTRICT
document_id     bigint NOT NULL REFERENCES idea_documents(id) ON DELETE RESTRICT  -- 산 시점 버전
buyer_id        uuid   NOT NULL REFERENCES users(id) ON DELETE RESTRICT
credits_paid    bigint NOT NULL
transaction_id  bigint NOT NULL REFERENCES credit_transactions(id)
purchased_at    timestamptz NOT NULL DEFAULT now()

UNIQUE (idea_id, buyer_id)   -- 한 사람이 같은 idea를 두 번 사지 않음
INDEX (buyer_id, purchased_at DESC)
```

> 정책: 새 버전이 발행돼도 이미 구매한 사람은 이전 `document_id`로 계속 접근 가능. 새 버전을 보고 싶으면 정책에 따라 추가 결제 또는 무상 업그레이드 (MVP에선 무상 업그레이드 추천).

---

## 4. 프로젝트

### 4.1 `projects`

```
id                  bigserial PK
idea_id             bigint REFERENCES ideas(id) ON DELETE SET NULL
leader_id           uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT
title               varchar(200) NOT NULL
summary             text
status              varchar(20) NOT NULL  -- DRAFT / RECRUITING / IN_PROGRESS / COMPLETED / ARCHIVED
recruitment_open    boolean NOT NULL DEFAULT false
max_members         int
repo_url            text
demo_url            text
idea_snapshot_md    text                  -- 채택 시점 idea 본문 복사
idea_snapshot_at    timestamptz
started_at          timestamptz
ended_at            timestamptz
deleted_at          timestamptz
created_at          timestamptz NOT NULL DEFAULT now()
updated_at          timestamptz NOT NULL DEFAULT now()

INDEX (status, created_at DESC)
INDEX (leader_id)
INDEX (idea_id)
```

### 4.2 `project_members`

```
id            bigserial PK
project_id    bigint NOT NULL REFERENCES projects(id) ON DELETE CASCADE
user_id       uuid   NOT NULL REFERENCES users(id) ON DELETE CASCADE
project_role  varchar(30) NOT NULL  -- LEADER / DEVELOPER / DESIGNER / TESTER 등
status        varchar(20) NOT NULL  -- PENDING / ACTIVE / LEFT / KICKED
joined_at     timestamptz
left_at       timestamptz
created_at    timestamptz NOT NULL DEFAULT now()

-- 같은 프로젝트에 active 상태로 동시에 두 번 가입 불가
CREATE UNIQUE INDEX project_members_active_unique
  ON project_members (project_id, user_id)
  WHERE left_at IS NULL;
```

### 4.3 `project_follows`

```
user_id     uuid   NOT NULL REFERENCES users(id) ON DELETE CASCADE
project_id  bigint NOT NULL REFERENCES projects(id) ON DELETE CASCADE
created_at  timestamptz NOT NULL DEFAULT now()

PRIMARY KEY (user_id, project_id)
INDEX (project_id)
```

---

## 5. 인터랙션

### 5.1 `hypes`

```
id          bigserial PK
user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE
idea_id     bigint REFERENCES ideas(id) ON DELETE CASCADE
project_id  bigint REFERENCES projects(id) ON DELETE CASCADE
created_at  timestamptz NOT NULL DEFAULT now()

CHECK ((idea_id IS NOT NULL)::int + (project_id IS NOT NULL)::int = 1)

CREATE UNIQUE INDEX hypes_user_idea_unique
  ON hypes (user_id, idea_id) WHERE idea_id IS NOT NULL;
CREATE UNIQUE INDEX hypes_user_project_unique
  ON hypes (user_id, project_id) WHERE project_id IS NOT NULL;

INDEX (idea_id) WHERE idea_id IS NOT NULL;
INDEX (project_id) WHERE project_id IS NOT NULL;
```

### 5.2 `rewards`

아이디어 채택, 인터뷰 참여, 어드민 보상 등 보상 이벤트의 메타.

```
id                 bigserial PK
idea_id            bigint REFERENCES ideas(id) ON DELETE SET NULL
project_id         bigint REFERENCES projects(id) ON DELETE SET NULL
recipient_user_id  uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT
created_by         uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT
reward_type        varchar(30) NOT NULL  -- ADOPTION / INTERVIEW / ADMIN / OTHER
amount             bigint NOT NULL CHECK (amount > 0)
status             varchar(20) NOT NULL  -- PENDING / APPROVED / PAID / CANCELLED
transaction_id     bigint REFERENCES credit_transactions(id)  -- 지급 시 채워짐
approved_at        timestamptz
paid_at            timestamptz
created_at         timestamptz NOT NULL DEFAULT now()
updated_at         timestamptz NOT NULL DEFAULT now()

INDEX (recipient_user_id, created_at DESC)
INDEX (status)
INDEX (project_id)
```

---

## 6. 게시판

### 6.1 `posts`

```
id          bigserial PK
author_id   uuid   NOT NULL REFERENCES users(id) ON DELETE RESTRICT
project_id  bigint REFERENCES projects(id) ON DELETE SET NULL  -- 프로젝트 홍보 시 연결
title       varchar(200) NOT NULL
content_md  text NOT NULL
post_type   varchar(20) NOT NULL  -- FREE / PROMO / BETA_RECRUIT / DEV_RECRUIT
status      varchar(20) NOT NULL DEFAULT 'PUBLISHED'  -- DRAFT / PUBLISHED / DELETED
deleted_at  timestamptz
created_at  timestamptz NOT NULL DEFAULT now()
updated_at  timestamptz NOT NULL DEFAULT now()

INDEX (status, post_type, created_at DESC)
INDEX (author_id)
INDEX (project_id)
```

### 6.2 `post_applications`

베타테스터·개발자 모집 공고에 지원.

```
id            bigserial PK
post_id       bigint NOT NULL REFERENCES posts(id) ON DELETE CASCADE
applicant_id  uuid   NOT NULL REFERENCES users(id) ON DELETE CASCADE
message       text
status        varchar(20) NOT NULL DEFAULT 'PENDING'  -- PENDING / ACCEPTED / REJECTED / WITHDRAWN
responded_at  timestamptz
created_at    timestamptz NOT NULL DEFAULT now()

UNIQUE (post_id, applicant_id)
INDEX (applicant_id, created_at DESC)
INDEX (post_id, status)
```

---

## 7. 댓글 (도메인별 분리)

세 테이블 모두 동일한 스키마. Spring에선 `@MappedSuperclass`로 추상화.

### 7.1 `idea_comments`
```
id          bigserial PK
idea_id     bigint NOT NULL REFERENCES ideas(id) ON DELETE CASCADE
author_id   uuid   NOT NULL REFERENCES users(id) ON DELETE RESTRICT
parent_id   bigint REFERENCES idea_comments(id) ON DELETE CASCADE
content     text NOT NULL
deleted_at  timestamptz
created_at  timestamptz NOT NULL DEFAULT now()
updated_at  timestamptz NOT NULL DEFAULT now()

INDEX (idea_id, created_at)
INDEX (author_id)
```

### 7.2 `project_comments`
스키마 동일. `idea_id` → `project_id`, `parent_id`는 self-ref.

### 7.3 `post_comments`
스키마 동일. `idea_id` → `post_id`, `parent_id`는 self-ref.

---

## 8. 알림 (자리만 마련, MVP 미구현)

### 8.1 `notifications`

```
id              bigserial PK
user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE
type            varchar(30) NOT NULL  -- HYPE_RECEIVED / IDEA_PURCHASED / REWARD_PAID / NEW_FOLLOWER 등
reference_type  varchar(30)
reference_id    bigint
payload         jsonb                 -- 알림 본문에 쓸 추가 데이터
is_read         boolean NOT NULL DEFAULT false
read_at         timestamptz
created_at      timestamptz NOT NULL DEFAULT now()

INDEX (user_id, is_read, created_at DESC)
```

---

## 9. 외래키 ON DELETE 정책 요약

| 시나리오 | 정책 |
|---|---|
| 사용자 탈퇴 시 본인 작성물 | 소프트 삭제 (`status='DELETED'` + `deleted_at`). hard delete 시 `RESTRICT`로 막음 |
| 사용자 탈퇴 시 좋아요·댓글 | `CASCADE` (작성자 정보 사라져도 OK) |
| 아이디어 hard delete | 일반적으로 `RESTRICT`. 보상 정산 끝난 것만 ADMIN이 수동 처리 |
| 프로젝트 삭제 시 멤버 | `CASCADE` |
| 게시물 삭제 시 댓글·지원 | `CASCADE` |
| 크레딧 트랜잭션 | 절대 삭제 금지 (`RESTRICT`). 정정은 새 ADJUST 트랜잭션으로 |

---

## 10. 인덱스 전략 (재요약)

피드 쿼리:
- `ideas (status, published_at DESC) WHERE status='PUBLISHED'`
- `projects (status, created_at DESC)`
- `posts (status, post_type, created_at DESC) WHERE status='PUBLISHED'`

검색:
- `idea_embeddings` ivfflat (벡터 유사도)
- `idea_embeddings` gin(keywords)
- 추후 full-text: `ideas` GIN(`to_tsvector('korean', title || ' ' || summary)`)

마이페이지:
- `ideas (author_id, created_at DESC)`
- `projects (leader_id, created_at DESC)`
- `posts (author_id, created_at DESC)`
- `idea_purchases (buyer_id, purchased_at DESC)`

크레딧:
- `credit_transactions (user_id, created_at DESC)`

---

## 11. 추가 확장 (MVP 이후)

- `reports` — 신고 시스템
- `messages` / `chat_rooms` — 사용자 간 채팅 (와이어프레임 언급됨)
- `payment_orders` — PG 충전 주문 추적
- `audit_logs` — 관리자 행동 로그
