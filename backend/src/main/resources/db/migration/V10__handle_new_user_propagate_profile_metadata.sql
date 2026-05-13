-- =====================================================================
-- V10: handle_new_user() 가 raw_user_meta_data 에서 프로필 4 필드 추가 추출
-- 참고: CLAUDE.md §4.7, §11
-- 의존: V3 (함수 + 트리거), V8 (nickname 추출 패턴), V9 (컬럼 추가)
--
-- 변경 사유: 가입 폼이 이름 / 생년월일 / 성별 / 프로필 이미지 입력 단계로 확장될 때
--           Supabase signUp 의 metadata 로 들어온 값을 자동으로 public.users 에 복사.
--           V8 의 nickname 추출과 동일 패턴 — NULLIF 로 빈 문자열 → NULL.
--
-- birth_date 캐스트는 EXCEPTION 으로 감싼다 — 클라이언트가 잘못된 형식 (예: '2020/01/01')
-- 으로 보내도 가입 자체가 실패하지 않고 birth_date 만 NULL 로 떨어진다. 사용자가 마이페이지에서
-- 다시 입력하면 됨.
--
-- 트리거 자체는 V3 의 on_auth_user_created 가 함수를 호출하는 구조라 함수 본문만
-- CREATE OR REPLACE 로 갱신.
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    CREATE OR REPLACE FUNCTION public.handle_new_user()
    RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = public, pg_temp
    AS $func$
    DECLARE
        v_nickname          text;
        v_real_name         text;
        v_birth_date        date;
        v_gender            text;
        v_profile_image_url text;
    BEGIN
        -- 닉네임 — V8 패턴 그대로.
        v_nickname := COALESCE(
            NULLIF(NEW.raw_user_meta_data ->> 'nickname', ''),
            'u-' || replace(NEW.id::text, '-', '')
        );

        -- btrim + NULLIF — 공백뿐 입력 ("   ") 도 NULL 로 정규화. User.blankToNull (Java isBlank()) 와
        -- 같은 기준으로 두 경로 (도메인 / DB trigger) 의 정규화 결과를 일관 유지.
        v_real_name         := NULLIF(btrim(NEW.raw_user_meta_data ->> 'real_name'), '');
        v_gender            := NULLIF(btrim(NEW.raw_user_meta_data ->> 'gender'), '');
        v_profile_image_url := NULLIF(btrim(NEW.raw_user_meta_data ->> 'profile_image_url'), '');

        -- birth_date 형식이 깨져도 가입 자체는 성공해야 한다.
        BEGIN
            v_birth_date := NULLIF(btrim(NEW.raw_user_meta_data ->> 'birth_date'), '')::date;
        EXCEPTION WHEN OTHERS THEN
            v_birth_date := NULL;
        END;

        INSERT INTO public.users (
            id, email, nickname, real_name, birth_date, gender, profile_image_url
        )
        VALUES (
            NEW.id, NEW.email, v_nickname,
            v_real_name, v_birth_date, v_gender, v_profile_image_url
        )
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO public.user_roles (user_id, role_id)
        VALUES (NEW.id, 1)
        ON CONFLICT (user_id, role_id) DO NOTHING;

        INSERT INTO public.user_credits (user_id)
        VALUES (NEW.id)
        ON CONFLICT (user_id) DO NOTHING;

        RETURN NEW;
    END;
    $func$;
END
$$;
