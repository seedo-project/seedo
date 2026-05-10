-- =====================================================================
-- V3: Supabase auth.users 동기화 (FK + handle_new_user 트리거)
-- 참고: 루트 CLAUDE.md §4.7 (USERS 동기화), §6.3, backend/CLAUDE.md "Supabase auth.users FK"
--
-- 전체 본문이 `auth` 스키마 존재 시에만 실행된다.
-- 로컬 Testcontainers PG 에는 auth 스키마가 없으므로 V3 는 noop 으로 통과 (Flyway success 처리).
-- Supabase 환경에선 auth 스키마가 존재하므로 FK + 함수 + 트리거가 실제로 생성된다.
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    -- ----- 1. public.users.id → auth.users(id) FK ----------------------
    -- 같은 V3 가 이미 한 번 적용된 환경에서 manual reapply 시 ALTER 충돌 방지용 가드.
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_users_auth_id'
          AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users
            ADD CONSTRAINT fk_users_auth_id
            FOREIGN KEY (id) REFERENCES auth.users(id) ON DELETE CASCADE;
    END IF;

    -- ----- 2. handle_new_user() 함수 -----------------------------------
    -- SECURITY DEFINER: 함수 소유자 권한으로 실행 (auth.users 트리거 컨텍스트에서 public 쓰기).
    -- SET search_path: schema-shadowing 공격 방지 (Supabase 권장 패턴).
    -- nickname: UUID 전체 hex 사용 → public.users.nickname UNIQUE 충돌 0% 보장.
    --           가입 직후 사용자가 변경하므로 가독성은 무관.
    -- 3개 INSERT 모두 ON CONFLICT DO NOTHING — 트리거 재발동/복구 시나리오 idempotent.
    CREATE OR REPLACE FUNCTION public.handle_new_user()
    RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = public, pg_temp
    AS $func$
    BEGIN
        INSERT INTO public.users (id, email, nickname)
        VALUES (
            NEW.id,
            NEW.email,
            'u-' || replace(NEW.id::text, '-', '')
        )
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO public.user_roles (user_id, role_id)
        VALUES (NEW.id, 1)  -- USER role (V1 시드)
        ON CONFLICT (user_id, role_id) DO NOTHING;

        INSERT INTO public.user_credits (user_id)
        VALUES (NEW.id)
        ON CONFLICT (user_id) DO NOTHING;

        RETURN NEW;
    END;
    $func$;

    -- ----- 3. auth.users AFTER INSERT 트리거 ---------------------------
    DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
    CREATE TRIGGER on_auth_user_created
        AFTER INSERT ON auth.users
        FOR EACH ROW
        EXECUTE FUNCTION public.handle_new_user();
END
$$;
