-- =====================================================================
-- V1: RBAC + 크레딧 초기 스키마
-- 참고: 루트 CLAUDE.md §5.1, §5.2, §5.8, §6
-- 주의: auth.users FK 와 handle_new_user() 트리거는 Supabase 전용
--       별도 마이그레이션에서 추가한다 (§6.3) — V1 에는 의도적으로 미포함.
-- =====================================================================


-- ----- 0. 공통 트리거 함수 ------------------------------------------
-- users.id 는 외부에서 명시적으로 박는다 (Supabase auth.users.id 와 동일 uuid).
-- V1 에선 uuid 기본 생성기가 필요 없으므로 pgcrypto 확장도 만들지 않는다.

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;


-- ----- 1. users -----------------------------------------------------
-- id 는 향후 Supabase auth.users.id 와 동일 uuid (FK 는 별도 마이그레이션).
-- hard delete 는 ADMIN 만, 기본은 status='DELETED' + deleted_at 소프트삭제.

CREATE TABLE users (
    id         uuid PRIMARY KEY,
    email      varchar(255) NOT NULL UNIQUE,
    nickname   varchar(50)  NOT NULL UNIQUE,
    status     varchar(20)  NOT NULL DEFAULT 'ACTIVE'
               CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    deleted_at timestamptz,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    -- 소프트삭제 불변식: status='DELETED' ↔ deleted_at NOT NULL.
    -- ACTIVE/SUSPENDED 인데 deleted_at 채워진 row, DELETED 인데 비워진 row 모두 차단.
    CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);

CREATE INDEX idx_users_nickname ON users(nickname);
CREATE INDEX idx_users_status   ON users(status) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ----- 2. roles, permissions ----------------------------------------

CREATE TABLE roles (
    id         bigserial PRIMARY KEY,
    code       varchar(50)  NOT NULL UNIQUE,
    name       varchar(100) NOT NULL,
    level      int          NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_roles_updated_at
BEFORE UPDATE ON roles
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE permissions (
    id          bigserial PRIMARY KEY,
    code        varchar(80)  NOT NULL UNIQUE,
    resource    varchar(50)  NOT NULL,
    action      varchar(50)  NOT NULL,
    description varchar(200),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_permissions_resource ON permissions(resource);

CREATE TRIGGER trg_permissions_updated_at
BEFORE UPDATE ON permissions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ----- 3. user_roles, role_permissions ------------------------------

-- RBAC 매핑 FK 정책: 모두 ON DELETE RESTRICT.
-- users / roles / permissions 는 hard delete 시 매핑이 조용히 사라지면 안 됨 — 권한 누락/과부여 사고 방지.
-- hard delete 가 필요하면 매핑부터 명시적으로 정리하게 강제 (CLAUDE.md §5.8, §6.4).
CREATE TABLE user_roles (
    id         bigserial PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role_id    bigint      NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    granted_at timestamptz NOT NULL DEFAULT now(),
    granted_by uuid                 REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE (user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);

CREATE TABLE role_permissions (
    id            bigserial PRIMARY KEY,
    role_id       bigint NOT NULL REFERENCES roles(id)       ON DELETE RESTRICT,
    permission_id bigint NOT NULL REFERENCES permissions(id) ON DELETE RESTRICT,
    UNIQUE (role_id, permission_id)
);


-- ----- 4. user_credits (잔액 캐시) ----------------------------------
-- balance 는 credit_transactions 합계의 캐시.
-- 변경은 항상 SELECT FOR UPDATE + 같은 트랜잭션의 transactions INSERT 와 함께 (CLAUDE.md §6.2, §8).

CREATE TABLE user_credits (
    user_id    uuid PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    balance    bigint      NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_user_credits_updated_at
BEFORE UPDATE ON user_credits
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ----- 5. credit_transactions (append-only 원장) --------------------
-- 절대 UPDATE/DELETE 금지 — 정정은 type='ADJUST' 새 row (CLAUDE.md §6.1).
-- 멱등성 키: (reference_type, reference_id) — PG webhook 중복 차단 등 (§6.7, §8.1).

CREATE TABLE credit_transactions (
    id             bigserial   PRIMARY KEY,
    user_id        uuid        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount         bigint      NOT NULL,            -- + 적립 / - 차감
    type           varchar(20) NOT NULL
                   CHECK (type IN ('CHARGE', 'SPEND', 'REWARD', 'REFUND', 'ADJUST')),
    balance_after  bigint      NOT NULL CHECK (balance_after >= 0),
    -- 타입-부호 정합성: 잘못된 부호로 잔액 캐시·원장 합계 어긋나는 사고 방지.
    --   CHARGE/REWARD/REFUND > 0  (잔액 증가)
    --   SPEND                < 0  (잔액 감소)
    --   ADJUST              != 0  (정정 — 양/음 모두 가능, 0 은 무의미)
    -- REFUND 부호 컨벤션: 환불 수령자의 잔액 증가 = 양수.
    CHECK (
        (type IN ('CHARGE', 'REWARD', 'REFUND') AND amount > 0) OR
        (type = 'SPEND'  AND amount < 0) OR
        (type = 'ADJUST' AND amount <> 0)
    ),
    reference_type varchar(50),                     -- PG_PAYMENT, IDEA_PURCHASE, ADOPTION, ...
    reference_id   varchar(100),
    description    varchar(255),
    created_at     timestamptz NOT NULL DEFAULT now(),
    -- reference 는 둘 다 있거나 둘 다 없거나 — 한쪽만 채워진 row 금지
    CHECK ((reference_type IS NULL) = (reference_id IS NULL))
);

CREATE INDEX idx_credit_tx_user_created
    ON credit_transactions(user_id, created_at DESC);

-- 멱등성 강제: 같은 (reference_type, reference_id) 두 번 INSERT 차단.
-- PG webhook 재시도, 채택 보상 중복 적재 등을 DB 레벨에서 막는다.
CREATE UNIQUE INDEX idx_credit_tx_reference
    ON credit_transactions(reference_type, reference_id)
    WHERE reference_type IS NOT NULL AND reference_id IS NOT NULL;

CREATE OR REPLACE FUNCTION block_credit_tx_modification() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'credit_transactions is append-only — UPDATE/DELETE 금지. 정정은 type=ADJUST 새 row 로 처리.';
END;
$$;

CREATE TRIGGER trg_credit_tx_block_update
BEFORE UPDATE ON credit_transactions
FOR EACH ROW EXECUTE FUNCTION block_credit_tx_modification();

CREATE TRIGGER trg_credit_tx_block_delete
BEFORE DELETE ON credit_transactions
FOR EACH ROW EXECUTE FUNCTION block_credit_tx_modification();


-- ----- 6. 시드: roles + permissions + role_permissions --------------

INSERT INTO roles (id, code, name, level) VALUES
    (1, 'USER',  '일반 사용자', 1),
    (2, 'ADMIN', '관리자',      2);
SELECT setval('roles_id_seq', (SELECT MAX(id) FROM roles));

INSERT INTO permissions (id, code, resource, action, description) VALUES
    (1,  'IDEA_CREATE',      'idea',    'create',  '아이디어 생성'),
    (2,  'IDEA_PUBLISH',     'idea',    'publish', '아이디어 발행 (DRAFT → PUBLISHED)'),
    (3,  'IDEA_ARCHIVE',     'idea',    'archive', '본인 아이디어 아카이브'),
    (4,  'IDEA_PURCHASE',    'idea',    'purchase','아이디어 본문 구매'),
    (5,  'IDEA_HARD_DELETE', 'idea',    'delete',  'hard delete (보상 정산 끝난 것만, ADMIN)'),
    (6,  'PROJECT_CREATE',   'project', 'create',  '아이디어 채택 → 프로젝트 생성'),
    (7,  'PROJECT_FOLLOW',   'project', 'follow',  '프로젝트 관심 등록'),
    (8,  'POST_CREATE',      'post',    'create',  '게시물 작성'),
    (9,  'POST_APPLY',       'post',    'apply',   '베타테스터/개발자 모집 지원'),
    (10, 'COMMENT_CREATE',   'comment', 'create',  '댓글 작성'),
    (11, 'HYPE_TOGGLE',      'hype',    'toggle',  'Hype 토글'),
    (12, 'CREDIT_REFUND',    'credit',  'refund',  '크레딧 환불 (ADMIN)'),
    (13, 'CREDIT_ADJUST',    'credit',  'adjust',  '크레딧 수동 정정 (ADMIN)'),
    (14, 'USER_SUSPEND',     'user',    'suspend', '사용자 정지 (ADMIN)');
SELECT setval('permissions_id_seq', (SELECT MAX(id) FROM permissions));

-- USER: 일반 도메인 권한 10개
INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, p.id FROM permissions p
WHERE p.code IN (
    'IDEA_CREATE', 'IDEA_PUBLISH', 'IDEA_ARCHIVE', 'IDEA_PURCHASE',
    'PROJECT_CREATE', 'PROJECT_FOLLOW',
    'POST_CREATE', 'POST_APPLY',
    'COMMENT_CREATE', 'HYPE_TOGGLE'
);

-- ADMIN: 전 권한 14개
INSERT INTO role_permissions (role_id, permission_id)
SELECT 2, p.id FROM permissions p;
