-- =====================================================================
-- V7: public_profiles 뷰 (닉네임 한 컬럼만 공개 SELECT 노출)
-- 참고: 루트 CLAUDE.md §7
-- 의존: V1 (users), V6 (RLS)
--
-- 배경: V6 의 users RLS 가 본인 row 만 SELECT 허용. 다른 사용자 닉네임을
-- 조회해야 하는 UI (아이디어 작성자, 프로젝트 leader 등) 는 익명으로만 표시됨.
-- email 등 민감 컬럼은 계속 격리하기 위해 view 로 닉네임만 분리 노출.
--
-- view 는 RLS 우회 — security_invoker (PG 15+) 또는 명시적 GRANT 만으로
-- 충분. 여기선 단순 GRANT.
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    -- 뷰 본체. 소프트 삭제된 사용자는 노출하지 않음.
    CREATE OR REPLACE VIEW public.public_profiles AS
    SELECT id, nickname
    FROM public.users
    WHERE deleted_at IS NULL;

    -- anon/authenticated 모두에게 SELECT 만 부여. 다른 권한 없음.
    GRANT SELECT ON public.public_profiles TO anon, authenticated;
END
$$;
