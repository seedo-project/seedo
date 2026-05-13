-- =====================================================================
-- V9: users 에 프로필 메타 컬럼 추가 (real_name / birth_date / gender / profile_image_url)
-- 참고: CLAUDE.md §5.1, 페이지 구조 S102-2 (회원가입), S501 (마이페이지 내 정보)
-- 의존: V1 (users 테이블)
--
-- 모든 컬럼 NULLABLE — 기존 row 와 호환 + FE 가 회원가입 폼을 단계적으로 확장 가능.
-- 본인 정보 조회 / 수정은 V6 RLS (id = auth.uid()) 로 Supabase 직결 처리. 별도 BE API 없음.
-- =====================================================================

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS real_name         varchar(50),
    ADD COLUMN IF NOT EXISTS birth_date        date,
    ADD COLUMN IF NOT EXISTS gender            varchar(20),
    ADD COLUMN IF NOT EXISTS profile_image_url text;

-- gender enum 후보를 CHECK 제약으로 강제 (Postgres enum 회피, CLAUDE.md §10.2).
-- NULL 도 허용 — 사용자가 미입력한 경우.
DO $$
BEGIN
    -- conname 만 검사하면 같은 이름의 constraint 가 다른 테이블에 있을 때 우리 users 에 추가 안 됨.
    -- conrelid (해당 테이블) + contype = 'c' (CHECK) 까지 한정해 정확히 우리 constraint 만 고른다.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'users_gender_check'
          AND conrelid = 'public.users'::regclass
          AND contype = 'c'
    ) THEN
        ALTER TABLE public.users
            ADD CONSTRAINT users_gender_check
            CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED'));
    END IF;
END $$;
