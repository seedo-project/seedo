-- =====================================================================
-- V18: project_follows — 프로젝트 팔로우 (업데이트 구독 의미) (#144)
-- 참고: 루트 CLAUDE.md §5.4, §7 (RLS), V12 hypes / V15 project_scraps 동일 토글 패턴
-- 의존: V1 (users), V4 (projects)
--
-- 정책:
--   - 스크랩(V15) 과 의미 분리: 스크랩 = "나중에 다시 보기", 팔로우 = "업데이트 구독".
--   - PK 복합 (user_id, project_id) — 같은 사용자가 같은 프로젝트 중복 팔로우 차단.
--   - user / project hard delete CASCADE.
--   - Supabase 직결 — Spring 백엔드 코드 없음.
-- =====================================================================

CREATE TABLE project_follows (
    user_id     uuid        NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    project_id  bigint      NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    followed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, project_id)
);

CREATE INDEX idx_project_follows_user
    ON project_follows(user_id, followed_at DESC);
CREATE INDEX idx_project_follows_project
    ON project_follows(project_id);

-- ----- RLS — Supabase 환경에서만 (V6 / V12 / V15 패턴) ----------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    ALTER TABLE public.project_follows ENABLE ROW LEVEL SECURITY;

    CREATE POLICY project_follows_public_select ON public.project_follows
        FOR SELECT TO public
        USING (true);

    CREATE POLICY project_follows_self_insert ON public.project_follows
        FOR INSERT TO authenticated
        WITH CHECK (user_id = auth.uid());

    CREATE POLICY project_follows_self_delete ON public.project_follows
        FOR DELETE TO authenticated
        USING (user_id = auth.uid());
END
$$;
