-- =====================================================================
-- V16: hypes / project_scraps RLS 좁힘 + 카운트 view (#142)
-- 참고: 루트 CLAUDE.md §7 (RLS 패턴), docs/code-review-lessons.md C.6
-- 의존: V12 (hypes), V15 (project_scraps)
--
-- 배경: V12 / V15 의 `_public_select USING (true)` 가 모든 사용자의 user_id ↔ entity_id 관계를
--      익명에게 노출했다. owned-record 의 RLS 정석 `USING (user_id = auth.uid())` 으로 좁히고,
--      카운트 노출은 별도 view 로 분리 (PR #141 CodeRabbit 리뷰).
--
-- 동작:
--   - hypes / project_scraps 의 public SELECT 정책 DROP → 본인 row 만 보이는 self_select 로 교체
--   - idea_hype_counts / project_hype_counts / project_scrap_counts 3 view 신설
--   - view 는 default SECURITY DEFINER 모드 (PG 15+: security_invoker 옵션 미지정) — view 소유자
--     (Supabase 의 postgres superuser) 권한으로 underlying 조회해 owner-only RLS 자연 우회.
--     카운트만 GROUP BY 결과로 노출하므로 user_id ↔ entity_id 관계는 누설되지 않는다.
--   - anon / authenticated role 에 SELECT GRANT 부여 — supabase-js 직결 가능.
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    -- ----- hypes: public SELECT 폐기 → 본인 row 만 -----
    DROP POLICY IF EXISTS hypes_public_select ON public.hypes;

    CREATE POLICY hypes_self_select ON public.hypes
        FOR SELECT TO authenticated
        USING (user_id = auth.uid());

    -- ----- project_scraps: 동일 -----
    DROP POLICY IF EXISTS project_scraps_public_select ON public.project_scraps;

    CREATE POLICY project_scraps_self_select ON public.project_scraps
        FOR SELECT TO authenticated
        USING (user_id = auth.uid());
END
$$;

-- ----- 카운트 view (집계 전용, RLS 우회) ------------------------------
-- View 가 auth 스키마 가드 밖에 있는 이유: testcontainers 같은 Supabase 외 환경에서도 view 자체는
-- 생성·검증 가능해야 한다 (RLS 정책만 Supabase 전용). view 동작은 환경 독립적.
--
-- 명시적으로 SECURITY INVOKER 를 끔 (default 동작 그대로 — view 소유자 권한으로 underlying 조회).
-- PG 15+ 의 security_invoker 옵션을 명시 안 하면 false 가 기본. Supabase 의 view 소유자는
-- postgres superuser 라 RLS 가 우회된다.

CREATE OR REPLACE VIEW idea_hype_counts AS
SELECT idea_id, count(*)::bigint AS count
FROM hypes
WHERE idea_id IS NOT NULL
GROUP BY idea_id;

CREATE OR REPLACE VIEW project_hype_counts AS
SELECT project_id, count(*)::bigint AS count
FROM hypes
WHERE project_id IS NOT NULL
GROUP BY project_id;

CREATE OR REPLACE VIEW project_scrap_counts AS
SELECT project_id, count(*)::bigint AS count
FROM project_scraps
GROUP BY project_id;

-- ----- view 권한 (Supabase 환경 전용) --------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    GRANT SELECT ON public.idea_hype_counts    TO anon, authenticated;
    GRANT SELECT ON public.project_hype_counts TO anon, authenticated;
    GRANT SELECT ON public.project_scrap_counts TO anon, authenticated;
END
$$;
