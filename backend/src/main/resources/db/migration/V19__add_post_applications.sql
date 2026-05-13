-- =====================================================================
-- V19: post_applications — 모집 글 지원 (#153)
-- 참고: 루트 CLAUDE.md §5.6, §5.8, §7 (RLS)
-- 의존: V1 (users, set_updated_at), V14 (posts)
--
-- 정책:
--   - posts.post_type IN ('BETA_RECRUIT', 'DEV_RECRUIT') 인 글에만 지원이 의미있지만,
--     post_type 검증은 RLS 가 아니라 프론트/도메인 단에서 수행 — RLS 로 끌어들이면
--     posts 조회 비용이 매 SELECT 마다 든다.
--   - UNIQUE(post_id, applicant_id) — 중복 지원 차단.
--   - post hard delete CASCADE (지원 이력은 글에 종속), applicant hard delete RESTRICT
--     (탈퇴 사용자가 지원했던 흔적은 ADMIN 정리 전까지 남는다).
--   - Supabase 직결 — Spring 백엔드 코드 없음.
-- =====================================================================

CREATE TABLE post_applications (
    id           bigserial   PRIMARY KEY,
    post_id      bigint      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    applicant_id uuid        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    message      text                 CHECK (message IS NULL OR char_length(message) BETWEEN 1 AND 2000),
    applied_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (post_id, applicant_id)
);

CREATE INDEX idx_post_applications_post
    ON post_applications(post_id, applied_at DESC);
CREATE INDEX idx_post_applications_applicant
    ON post_applications(applicant_id, applied_at DESC);

-- ----- RLS — Supabase 환경에서만 (V6 / V12 패턴) ----------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    ALTER TABLE public.post_applications ENABLE ROW LEVEL SECURITY;

    -- 본인이 지원한 row SELECT — "이미 지원했는지" 표시용.
    CREATE POLICY post_applications_applicant_select ON public.post_applications
        FOR SELECT TO authenticated
        USING (applicant_id = auth.uid());

    -- 게시물 작성자 SELECT — 지원자 명단 노출. 삭제된 글은 못 본다.
    CREATE POLICY post_applications_post_author_select ON public.post_applications
        FOR SELECT TO authenticated
        USING (EXISTS (
            SELECT 1 FROM public.posts p
            WHERE p.id = post_applications.post_id
              AND p.author_id = auth.uid()
              AND p.deleted_at IS NULL
        ));

    -- 본인 INSERT — applicant_id = auth.uid() 강제. 모집 글(BETA/DEV) + PUBLISHED 한정.
    -- 프론트가 같은 가드를 갖고 있지만 직접 호출 우회를 막기 위한 RLS 차원 방어.
    CREATE POLICY post_applications_self_insert ON public.post_applications
        FOR INSERT TO authenticated
        WITH CHECK (
            applicant_id = auth.uid()
            AND EXISTS (
                SELECT 1 FROM public.posts p
                WHERE p.id = post_applications.post_id
                  AND p.status = 'PUBLISHED'
                  AND p.post_type IN ('BETA_RECRUIT', 'DEV_RECRUIT')
                  AND p.deleted_at IS NULL
            )
        );

    -- 본인 DELETE — 지원 취소.
    CREATE POLICY post_applications_self_delete ON public.post_applications
        FOR DELETE TO authenticated
        USING (applicant_id = auth.uid());
END
$$;
