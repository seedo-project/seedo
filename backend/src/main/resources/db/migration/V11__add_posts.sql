-- =====================================================================
-- V11: 게시판 — posts + post_comments
-- 참고: 루트 CLAUDE.md §5.6, §5.7, §5.8
-- 의존: V1 (users, set_updated_at), V4 (projects), V10 (comments 패턴)
--
-- 정책:
--   - post_type ∈ {FREE, PROMO, BETA_RECRUIT, DEV_RECRUIT}.
--   - project_id 는 nullable — FREE 글은 프로젝트와 무관, PROMO/모집글은 연결 가능.
--   - 작성자 hard delete RESTRICT (소프트 삭제 우선). 프로젝트 hard delete → CASCADE.
--   - status: PUBLISHED 기본, DELETED 는 soft (§6.4).
--   - post_applications 는 모집 흐름과 함께 별도 마이그레이션.
-- =====================================================================

CREATE TABLE posts (
    id         bigserial   PRIMARY KEY,
    author_id  uuid        NOT NULL REFERENCES users(id)    ON DELETE RESTRICT,
    project_id bigint               REFERENCES projects(id) ON DELETE CASCADE,
    post_type  varchar(20) NOT NULL CHECK (post_type IN ('FREE', 'PROMO', 'BETA_RECRUIT', 'DEV_RECRUIT')),
    title      varchar(200) NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    body       text        NOT NULL CHECK (char_length(body)  BETWEEN 1 AND 20000),
    status     varchar(20) NOT NULL DEFAULT 'PUBLISHED'
               CHECK (status IN ('DRAFT', 'PUBLISHED', 'DELETED')),
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);

CREATE INDEX idx_posts_type_created
    ON posts(post_type, created_at DESC)
    WHERE deleted_at IS NULL AND status = 'PUBLISHED';
CREATE INDEX idx_posts_author
    ON posts(author_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_project
    ON posts(project_id) WHERE project_id IS NOT NULL AND deleted_at IS NULL;

CREATE TRIGGER trg_posts_updated_at
BEFORE UPDATE ON posts
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ----- post_comments — V10 idea/project_comments 동일 패턴 -----------
CREATE TABLE post_comments (
    id         bigserial   PRIMARY KEY,
    post_id    bigint      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id  uuid        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    content    text        NOT NULL CHECK (char_length(content) BETWEEN 1 AND 2000),
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_post_comments_post_created
    ON post_comments(post_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_post_comments_author
    ON post_comments(author_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_post_comments_updated_at
BEFORE UPDATE ON post_comments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ----- RLS — Supabase 환경에서만 (V6 패턴) ---------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    ALTER TABLE public.posts         ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.post_comments ENABLE ROW LEVEL SECURITY;

    -- posts: 공개 SELECT (PUBLISHED & not deleted) + 본인 작성건 전체.
    CREATE POLICY posts_public_select ON public.posts
        FOR SELECT TO public
        USING (status = 'PUBLISHED' AND deleted_at IS NULL);
    CREATE POLICY posts_author_select ON public.posts
        FOR SELECT TO authenticated
        USING (author_id = auth.uid());
    CREATE POLICY posts_author_insert ON public.posts
        FOR INSERT TO authenticated
        WITH CHECK (author_id = auth.uid());
    CREATE POLICY posts_author_update ON public.posts
        FOR UPDATE TO authenticated
        USING (author_id = auth.uid())
        WITH CHECK (author_id = auth.uid());

    -- post_comments: V10 동일 패턴.
    CREATE POLICY post_comments_public_select ON public.post_comments
        FOR SELECT TO public
        USING (deleted_at IS NULL);
    CREATE POLICY post_comments_author_insert ON public.post_comments
        FOR INSERT TO authenticated
        WITH CHECK (author_id = auth.uid());
    CREATE POLICY post_comments_author_update ON public.post_comments
        FOR UPDATE TO authenticated
        USING (author_id = auth.uid())
        WITH CHECK (author_id = auth.uid());
END
$$;
