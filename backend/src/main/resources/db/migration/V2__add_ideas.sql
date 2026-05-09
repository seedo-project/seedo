-- =====================================================================
-- V2: idea 도메인
-- 참고: 루트 CLAUDE.md §5.3, §6.5 (버전 보존), §6.6 (본인 구매 차단), §10.2
-- 의존: V1 (users, credit_transactions, set_updated_at())
-- =====================================================================


-- ----- 0. pgvector 확장 ---------------------------------------------
-- idea_embeddings.embedding vector(1536). Supabase 는 기본 활성화돼 있지만
-- 로컬/Testcontainers 까지 동일 보장하기 위해 명시적으로 둔다.

CREATE EXTENSION IF NOT EXISTS vector;


-- ----- 1. ideas -----------------------------------------------------
-- author hard delete 는 RESTRICT — 작성자는 본인 아이디어부터 archive (§5.8).
-- current_version_id 는 idea_documents 가 만들어진 후 ALTER 로 FK 부착 (순환 참조 회피).
-- DRAFT 단계엔 NULL, finalize 후 idea_documents.id 가 채워진다.

CREATE TABLE ideas (
    id                 bigserial PRIMARY KEY,
    author_id          uuid        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status             varchar(20) NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'DELETED')),
    current_version_id bigint,
    price_credits      int         NOT NULL DEFAULT 10 CHECK (price_credits  >= 0),
    reward_credits     int         NOT NULL DEFAULT 5  CHECK (reward_credits >= 0),
    deleted_at         timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    -- 소프트삭제 양방향 정합성: status='DELETED' ↔ deleted_at NOT NULL (users 와 동일 패턴, §6.4).
    CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);

CREATE INDEX idx_ideas_author
    ON ideas(author_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_ideas_status_created
    ON ideas(status, created_at DESC) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_ideas_updated_at
BEFORE UPDATE ON ideas
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ----- 2. idea_documents (버전별 본문) ------------------------------
-- (idea_id, version) UNIQUE — published 후 수정도 새 row, 기존 보존 (§6.5).
-- title 은 doc 단위로 저장 (markdown 본문 첫 헤딩 캐시 또는 별도 입력).

CREATE TABLE idea_documents (
    id         bigserial PRIMARY KEY,
    idea_id    bigint       NOT NULL REFERENCES ideas(id) ON DELETE RESTRICT,
    version    int          NOT NULL CHECK (version >= 1),
    title      varchar(200) NOT NULL,
    content_md text         NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (idea_id, version)
);

-- 순환 FK 부착: ideas.current_version_id → idea_documents.id.
-- RESTRICT — 현재 버전 row 가 사라져 참조가 끊기는 것을 막는다.
ALTER TABLE ideas
    ADD CONSTRAINT ideas_current_version_id_fkey
    FOREIGN KEY (current_version_id) REFERENCES idea_documents(id) ON DELETE RESTRICT;


-- ----- 3. idea_chat_sessions ----------------------------------------
-- finalize 시 idea_id + finalized_at 채워짐, abandon 시 abandoned_at 채워짐.
-- 상태별 양방향 CHECK 로 비정합 row 차단 (예: FINALIZED 인데 idea_id NULL).

CREATE TABLE idea_chat_sessions (
    id           bigserial PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    idea_id      bigint               REFERENCES ideas(id) ON DELETE RESTRICT,
    status       varchar(20) NOT NULL DEFAULT 'IN_PROGRESS'
                 CHECK (status IN ('IN_PROGRESS', 'FINALIZED', 'ABANDONED')),
    finalized_at timestamptz,
    abandoned_at timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    -- 상태 ↔ 부수 컬럼 양방향 정합성
    CHECK ((status = 'FINALIZED') = (idea_id      IS NOT NULL)),
    CHECK ((status = 'FINALIZED') = (finalized_at IS NOT NULL)),
    CHECK ((status = 'ABANDONED') = (abandoned_at IS NOT NULL))
);

CREATE INDEX idx_idea_chat_sessions_user
    ON idea_chat_sessions(user_id, created_at DESC);

CREATE TRIGGER trg_idea_chat_sessions_updated_at
BEFORE UPDATE ON idea_chat_sessions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ----- 4. idea_chat_messages ----------------------------------------
-- session 삭제 시 메시지 CASCADE — 채팅 로그는 세션에 종속된 derived data.

CREATE TABLE idea_chat_messages (
    id         bigserial PRIMARY KEY,
    session_id bigint      NOT NULL REFERENCES idea_chat_sessions(id) ON DELETE CASCADE,
    role       varchar(20) NOT NULL
               CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content    text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_idea_chat_messages_session
    ON idea_chat_messages(session_id, created_at);


-- ----- 5. idea_embeddings (검색용) ----------------------------------
-- ideas 와 1:1. embedding 은 OpenAI text-embedding-3-small (1536D, §11).
-- ivfflat lists=100 은 초기값 — 데이터 충분히 쌓이면 sqrt(rows) 기준으로 재튜닝.
-- 임베딩은 derived data → ideas hard delete 시 CASCADE 로 함께 정리.

CREATE TABLE idea_embeddings (
    idea_id    bigint        PRIMARY KEY REFERENCES ideas(id) ON DELETE CASCADE,
    embedding  vector(1536)  NOT NULL,
    keywords   text[]        NOT NULL DEFAULT '{}',
    created_at timestamptz   NOT NULL DEFAULT now(),
    updated_at timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_idea_embeddings_vector
    ON idea_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_idea_embeddings_keywords
    ON idea_embeddings USING GIN (keywords);

CREATE TRIGGER trg_idea_embeddings_updated_at
BEFORE UPDATE ON idea_embeddings
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ----- 6. idea_purchases --------------------------------------------
-- (idea_id, buyer_id) UNIQUE — 한 사용자가 한 아이디어 한 번만 구매.
-- transaction_id UNIQUE — credit_transactions 의 SPEND 행과 1:1. 멱등성 보강.
-- document_id 는 산 시점 스냅샷 — 이후 새 버전이 발행돼도 구매자는 산 버전 보존 (§6.5).

CREATE TABLE idea_purchases (
    id             bigserial PRIMARY KEY,
    idea_id        bigint      NOT NULL REFERENCES ideas(id)               ON DELETE RESTRICT,
    buyer_id       uuid        NOT NULL REFERENCES users(id)               ON DELETE RESTRICT,
    document_id    bigint      NOT NULL REFERENCES idea_documents(id)      ON DELETE RESTRICT,
    transaction_id bigint      NOT NULL REFERENCES credit_transactions(id) ON DELETE RESTRICT,
    created_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (idea_id, buyer_id),
    UNIQUE (transaction_id)
);

CREATE INDEX idx_idea_purchases_buyer
    ON idea_purchases(buyer_id, created_at DESC);

-- 본인 구매 차단 (§6.6) — author_id == buyer_id 인 INSERT 를 트리거에서 막는다.
-- 도메인 레이어 검증 우회·조작이 있어도 DB 가 마지막 방어선.
CREATE OR REPLACE FUNCTION block_self_purchase() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    idea_author uuid;
BEGIN
    SELECT author_id INTO idea_author FROM ideas WHERE id = NEW.idea_id;
    IF idea_author = NEW.buyer_id THEN
        RAISE EXCEPTION
            '본인 아이디어는 구매할 수 없습니다 — author_id = buyer_id (CLAUDE.md §6.6).';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_idea_purchases_block_self
BEFORE INSERT ON idea_purchases
FOR EACH ROW EXECUTE FUNCTION block_self_purchase();
