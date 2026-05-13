-- =====================================================================
-- V20: handle_new_user() 트리거에 gender 입력 정규화 추가 (#175)
-- 의존: V3 (함수 + 트리거), V9 (users_gender_check), V10 (직전 함수 본문)
--
-- 변경 사유: FE 가입 폼이 gender 를 소문자 ('male' / 'female' / 'unspecified') 로 보내는데,
--           V9 의 users_gender_check 는 대문자 ('MALE' / 'FEMALE' / 'OTHER' / 'UNDISCLOSED') 만 허용 →
--           트리거 안에서 CHECK 위반 → Supabase 회원가입 500. FE 케이스를 백엔드 트리거에서 정규화해서
--           양쪽 모두 robust 하게 (#175).
--
-- 정규화 규칙:
--   - btrim + upper → 'MALE' / 'FEMALE' / 'UNSPECIFIED' / 'OTHER' / 그 외
--   - 'UNSPECIFIED' → 'UNDISCLOSED' 로 매핑 (FE 의 \"미선택\" 옵션을 DB 의 \"공개하지 않음\" 값으로)
--   - CHECK 가 허용하는 4 개 값 외에는 NULL 로 떨어뜨림 (birth_date 패턴과 동일 — 가입은 성공)
--
-- V10 본문을 거의 그대로 복사 + gender 정규화 블록만 강화. CREATE OR REPLACE 라 트리거 자체는 V3 의
-- on_auth_user_created 가 호출하는 구조 유지.
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

        v_real_name         := NULLIF(btrim(NEW.raw_user_meta_data ->> 'real_name'), '');
        v_profile_image_url := NULLIF(btrim(NEW.raw_user_meta_data ->> 'profile_image_url'), '');

        -- gender 정규화 — 소문자 입력 ('male') 도 받아 대문자로 통일하고 CHECK 미허용 값은 NULL.
        -- FE 의 'unspecified' → DB 의 'UNDISCLOSED' 로 매핑 (#175).
        v_gender := upper(NULLIF(btrim(NEW.raw_user_meta_data ->> 'gender'), ''));
        IF v_gender = 'UNSPECIFIED' THEN
            v_gender := 'UNDISCLOSED';
        END IF;
        IF v_gender IS NOT NULL
           AND v_gender NOT IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED') THEN
            -- CHECK 안 통과하는 입력 (오타 / 알 수 없는 값) 은 NULL 로. birth_date 패턴 따라 가입 자체는 성공.
            v_gender := NULL;
        END IF;

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
