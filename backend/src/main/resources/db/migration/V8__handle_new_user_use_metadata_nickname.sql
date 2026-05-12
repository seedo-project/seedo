-- =====================================================================
-- V8: handle_new_user() 가 raw_user_meta_data->>'nickname' 우선 사용
-- 참고: CLAUDE.md §4.7
-- 의존: V3 (handle_new_user 함수 + 트리거)
--
-- 변경 사유: 가입 폼이 닉네임 입력 단계를 받기 시작 (#116). 메타데이터에 들어온
--           닉네임을 그대로 public.users.nickname 에 저장. 없으면 V3 fallback (UUID hex) 유지.
--
-- 트리거 자체는 V3 의 on_auth_user_created 가 함수를 호출하는 구조라
-- 함수 본문만 CREATE OR REPLACE 로 갱신하면 충분 (트리거 재부착 불필요).
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
        v_nickname text;
    BEGIN
        -- metadata.nickname 이 비어있지 않으면 그대로, 아니면 UUID hex fallback.
        -- UNIQUE 충돌 시 raise — 클라이언트 (Supabase signUp) 에 그대로 전파되어 auth.users INSERT 가 롤백된다.
        v_nickname := COALESCE(
            NULLIF(NEW.raw_user_meta_data ->> 'nickname', ''),
            'u-' || replace(NEW.id::text, '-', '')
        );

        INSERT INTO public.users (id, email, nickname)
        VALUES (NEW.id, NEW.email, v_nickname)
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
