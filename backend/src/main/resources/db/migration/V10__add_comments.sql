-- =====================================================================
-- V10: 댓글 — idea_comments / project_comments
-- 참고: 루트 CLAUDE.md §5.7, §5.8, §6.4 (소프트 삭제)
-- 의존: V1 (users), V2 (ideas), V4 (projects)
--
-- 정책:
--   - 동일 스키마 2 테이블 (post_comments 는 posts 테이블 신설 후 V11+ 에서 같이).
--   - 부모(idea/project) hard delete → 댓글 CASCADE (§5.8).
--   - 작성자 hard delete → RESTRICT (§5.8, 사용자 소프트 삭제 우선).
--   - 본문 수정 가능. 삭제는 deleted_at 세팅 (소프트). hard delete 정책 없음.
-- =====================================================================

CREATE TABLE idea_comments (
    id         bigserial   PRIMARY KEY,
    idea_id    bigint      NOT NULL REFERENCES ideas(id) ON DELETE CASCADE,
    author_id  uuid        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    content    text        NOT NULL CHECK (char_length(content) BETWEEN 1 AND 2000),
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_idea_comments_idea_created
    ON idea_comments(idea_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_idea_comments_author
    ON idea_comments(author_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_idea_comments_updated_at
BEFORE UPDATE ON idea_comments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE project_comments (
    id         bigserial   PRIMARY KEY,
    project_id bigint      NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    author_id  uuid        NOT NULL REFERENCES users(id)    ON DELETE RESTRICT,
    content    text        NOT NULL CHECK (char_length(content) BETWEEN 1 AND 2000),
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_comments_project_created
    ON project_comments(project_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_project_comments_author
    ON project_comments(author_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_project_comments_updated_at
BEFORE UPDATE ON project_comments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ----- RLS — Supabase 환경에서만 (V6 패턴) ---------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    ALTER TABLE public.idea_comments    ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.project_comments ENABLE ROW LEVEL SECURITY;

    -- 공개 SELECT — 삭제되지 않은 댓글만.
    CREATE POLICY idea_comments_public_select ON public.idea_comments
        FOR SELECT TO public
        USING (deleted_at IS NULL);
    CREATE POLICY project_comments_public_select ON public.project_comments
        FOR SELECT TO public
        USING (deleted_at IS NULL);

    -- 본인 작성 INSERT.
    CREATE POLICY idea_comments_author_insert ON public.idea_comments
        FOR INSERT TO authenticated
        WITH CHECK (author_id = auth.uid());
    CREATE POLICY project_comments_author_insert ON public.project_comments
        FOR INSERT TO authenticated
        WITH CHECK (author_id = auth.uid());

    -- 본인 UPDATE — 본문 수정 + deleted_at 세팅(소프트 삭제) 둘 다 이걸로 처리.
    CREATE POLICY idea_comments_author_update ON public.idea_comments
        FOR UPDATE TO authenticated
        USING (author_id = auth.uid())
        WITH CHECK (author_id = auth.uid());
    CREATE POLICY project_comments_author_update ON public.project_comments
        FOR UPDATE TO authenticated
        USING (author_id = auth.uid())
        WITH CHECK (author_id = auth.uid());

    -- hard DELETE 정책 없음 — service_role / ADMIN 전용.
END
$$;
