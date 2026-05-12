-- =====================================================================
-- V9: hypes (좋아요) — idea XOR project 토글
-- 참고: 루트 CLAUDE.md §5.5 (hypes 스키마), §12 (토글 정책), §7 (RLS 패턴)
-- 의존: V1 (users), V2 (ideas), V4 (projects)
--
-- 정책:
--   - 한 row 는 idea 또는 project 중 정확히 하나만 가리킨다 (XOR CHECK).
--   - (user_id, idea_id) / (user_id, project_id) 는 각각 partial UNIQUE — 같은 대상에 중복 좋아요 차단.
--   - 토글 UI: 누르면 INSERT, 다시 누르면 DELETE (§12). 별도 deleted_at 안 둠.
--   - 대상이 hard delete 되면 hypes 도 CASCADE — 좋아요는 메타 정보일 뿐.
-- =====================================================================

CREATE TABLE hypes (
    id         bigserial PRIMARY KEY,
    user_id    uuid       NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    idea_id    bigint              REFERENCES ideas(id)    ON DELETE CASCADE,
    project_id bigint              REFERENCES projects(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((idea_id IS NOT NULL) <> (project_id IS NOT NULL))
);

CREATE UNIQUE INDEX uq_hypes_user_idea
    ON hypes(user_id, idea_id)
    WHERE idea_id IS NOT NULL;
CREATE UNIQUE INDEX uq_hypes_user_project
    ON hypes(user_id, project_id)
    WHERE project_id IS NOT NULL;

CREATE INDEX idx_hypes_idea    ON hypes(idea_id)    WHERE idea_id    IS NOT NULL;
CREATE INDEX idx_hypes_project ON hypes(project_id) WHERE project_id IS NOT NULL;

-- ----- RLS — Supabase 환경에서만 (V6 패턴) ---------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    ALTER TABLE public.hypes ENABLE ROW LEVEL SECURITY;

    -- 카운트/내 좋아요 여부 표시에 필요 — 공개 SELECT.
    CREATE POLICY hypes_public_select ON public.hypes
        FOR SELECT TO public
        USING (true);

    CREATE POLICY hypes_self_insert ON public.hypes
        FOR INSERT TO authenticated
        WITH CHECK (user_id = auth.uid());

    CREATE POLICY hypes_self_delete ON public.hypes
        FOR DELETE TO authenticated
        USING (user_id = auth.uid());
END
$$;
