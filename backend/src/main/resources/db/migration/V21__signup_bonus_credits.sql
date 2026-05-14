-- =====================================================================
-- V21: 신규 가입자에게 300 크레딧 자동 부여 + 기존 잔액 0 사용자 backfill (#185)
-- 의존: V20 (handle_new_user 직전 본문), V9 (gender CHECK), V1 (user_credits / credit_transactions)
--
-- 정책 (CLAUDE.md §12 MVP 무료 크레딧):
--   - 가입 시 user_credits.balance 를 default 0 대신 300 으로 시작
--   - credit_transactions 에 (type=ADJUST, amount=+300, description='가입 보너스') 원장 함께 INSERT
--     → 잔액 + 원장 같은 트랜잭션 (§6 의 핵심 불변)
--   - 같은 사용자 중복 부여 방지 — NOT EXISTS 가드 (가입 보너스 원장 존재 시 skip)
--
-- backfill 정책 (사용자 결정):
--   - 잔액 0 + 가입 보너스 원장 없는 기존 사용자만 300 적립
--   - 이미 한 번이라도 활동한 사용자 (잔액 > 0 또는 보너스 원장 있음) 는 건드리지 않음
-- =====================================================================

-- ----- 1. handle_new_user 함수 갱신 — V20 본문 + 가입 보너스 부분 ----
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
        SIGNUP_BONUS        constant bigint := 300;
    BEGIN
        v_nickname := COALESCE(
            NULLIF(NEW.raw_user_meta_data ->> 'nickname', ''),
            'u-' || replace(NEW.id::text, '-', '')
        );

        v_real_name         := NULLIF(btrim(NEW.raw_user_meta_data ->> 'real_name'), '');
        v_profile_image_url := NULLIF(btrim(NEW.raw_user_meta_data ->> 'profile_image_url'), '');

        -- gender 정규화 (V20): 소문자 입력 → 대문자, UNSPECIFIED → UNDISCLOSED, CHECK 미허용 → NULL.
        v_gender := upper(NULLIF(btrim(NEW.raw_user_meta_data ->> 'gender'), ''));
        IF v_gender = 'UNSPECIFIED' THEN
            v_gender := 'UNDISCLOSED';
        END IF;
        IF v_gender IS NOT NULL
           AND v_gender NOT IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED') THEN
            v_gender := NULL;
        END IF;

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

        -- 가입 보너스 — default 0 대신 300 으로 INSERT (#185).
        INSERT INTO public.user_credits (user_id, balance)
        VALUES (NEW.id, SIGNUP_BONUS)
        ON CONFLICT (user_id) DO NOTHING;

        -- 원장 — 잔액 변경과 같은 트랜잭션. 중복 부여 방지 NOT EXISTS 가드.
        IF NOT EXISTS (
            SELECT 1 FROM public.credit_transactions
            WHERE user_id = NEW.id
              AND type = 'ADJUST'
              AND description = '가입 보너스'
        ) THEN
            INSERT INTO public.credit_transactions (
                user_id, amount, type, balance_after, description
            )
            VALUES (NEW.id, SIGNUP_BONUS, 'ADJUST', SIGNUP_BONUS, '가입 보너스');
        END IF;

        RETURN NEW;
    END;
    $func$;
END
$$;

-- ----- 2. 기존 사용자 backfill — 잔액 0 + 보너스 원장 없는 사람만 -----
DO $$
DECLARE
    SIGNUP_BONUS constant bigint := 300;
    target_user uuid;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    FOR target_user IN
        SELECT uc.user_id
        FROM public.user_credits uc
        WHERE uc.balance = 0
          AND NOT EXISTS (
              SELECT 1 FROM public.credit_transactions ct
              WHERE ct.user_id = uc.user_id
                AND ct.type = 'ADJUST'
                AND ct.description = '가입 보너스'
          )
    LOOP
        -- 잔액 + 원장 같은 (DO 블록 자동) 트랜잭션. credit_transactions 의 append-only 트리거가
        -- INSERT 만 허용하므로 별도 가드 불필요.
        UPDATE public.user_credits
        SET balance = balance + SIGNUP_BONUS
        WHERE user_id = target_user;

        INSERT INTO public.credit_transactions (
            user_id, amount, type, balance_after, description
        )
        VALUES (target_user, SIGNUP_BONUS, 'ADJUST', SIGNUP_BONUS, '가입 보너스');
    END LOOP;
END
$$;
