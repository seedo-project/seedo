-- =====================================================================
-- V11: public_profiles 뷰에 profile_image_url 컬럼 추가
-- 참고: CLAUDE.md §7
-- 의존: V7 (public_profiles 뷰), V9 (profile_image_url 컬럼)
--
-- 배경: 다른 사용자 프로필 카드 (아이디어 작성자, 프로젝트 leader 등) 에 프로필 이미지를
-- 표시하려면 v_users 의 profile_image_url 이 공개 SELECT 로 노출돼야 한다.
-- 이름 / 생년월일 / 성별은 비공개 — 뷰에서 의도적으로 제외.
--
-- view 갱신은 CREATE OR REPLACE — V7 의 GRANT 는 그대로 유지된다 (뷰 정의 교체만).
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    CREATE OR REPLACE VIEW public.public_profiles AS
    SELECT id, nickname, profile_image_url
    FROM public.users
    WHERE deleted_at IS NULL;
END
$$;
