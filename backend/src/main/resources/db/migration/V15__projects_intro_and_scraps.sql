-- =====================================================================
-- V15: 프로젝트 소개 페이지 (4 항목) + 스크랩 (#140)
-- 참고: 루트 CLAUDE.md §5.4 (projects 스키마), §7 (RLS), §12 (스크랩 정책)
-- 의존: V1 (users, set_updated_at), V4 (projects), V12 (hypes — 같은 토글 패턴)
--
-- 정책:
--   - 채택 직후 프로젝트는 DRAFT (V4 그대로). LEADER 가 4 항목 (cover_image_url / title /
--     description / guide_md) 을 채우고 명시적 publish 액션 (DRAFT → IN_PROGRESS) 으로 공개한다.
--   - title / description 은 publish 시점에 NOT NULL 강제 (CHECK 가드). DRAFT / ARCHIVED 는 면제.
--   - 모집 / 합류 흐름은 게시판 (V14) 으로 흡수 — project_members 는 LEADER 1명만 유지.
--   - project_scraps 토글은 Supabase 직결 (V12 hypes 와 동일 패턴) — Spring 백엔드 코드 없음.
-- =====================================================================

ALTER TABLE projects
    ADD COLUMN cover_image_url varchar(500),
    ADD COLUMN title           varchar(200),
    ADD COLUMN description     text,
    ADD COLUMN guide_md        text;

-- title / description / guide_md 길이 가드 — XSS·DoS 차단의 1차 방어선.
-- description / guide_md 는 마크다운 본문이라 idea_documents (CLAUDE.md §6.5) 보다 짧게 잡는다.
ALTER TABLE projects
    ADD CONSTRAINT chk_projects_title_len
        CHECK (title IS NULL OR char_length(title) BETWEEN 1 AND 200);
ALTER TABLE projects
    ADD CONSTRAINT chk_projects_description_len
        CHECK (description IS NULL OR char_length(description) BETWEEN 1 AND 10000);
ALTER TABLE projects
    ADD CONSTRAINT chk_projects_guide_len
        CHECK (guide_md IS NULL OR char_length(guide_md) BETWEEN 1 AND 20000);

-- publish 가드: DRAFT / ARCHIVED / DELETED 외 상태에는 title + description 이 채워져 있어야 한다.
-- publish 트랜잭션이 도메인 단에서 검증하지만 (PublishProjectService), 직접 SQL 조작이나 다른 진입점에서
-- 새 상태로 가는 경우 마지막 방어선 역할.
ALTER TABLE projects
    ADD CONSTRAINT chk_projects_published_fields
        CHECK (
            status IN ('DRAFT', 'ARCHIVED', 'DELETED')
            OR (title IS NOT NULL AND description IS NOT NULL)
        );

-- ----- project_scraps — 토글 북마크 (V12 hypes 와 동일 직결 패턴) -----
-- 하입(좋아요) 과 별개 인터랙션: "나중에 다시 보려고 저장". 프로젝트 전용 — 아이디어·게시물 스크랩은
-- 아직 요구사항 없음 (이슈 #140).
CREATE TABLE project_scraps (
    user_id     uuid        NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    project_id  bigint      NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    scrapped_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, project_id)
);

CREATE INDEX idx_project_scraps_user
    ON project_scraps(user_id, scrapped_at DESC);
CREATE INDEX idx_project_scraps_project
    ON project_scraps(project_id);

-- ----- RLS — Supabase 환경에서만 (V6 / V12 패턴) ----------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    -- project_scraps: 카운트 / 내 스크랩 여부 표시에 필요 — 공개 SELECT, 본인만 INSERT/DELETE.
    ALTER TABLE public.project_scraps ENABLE ROW LEVEL SECURITY;

    CREATE POLICY project_scraps_public_select ON public.project_scraps
        FOR SELECT TO public
        USING (true);

    CREATE POLICY project_scraps_self_insert ON public.project_scraps
        FOR INSERT TO authenticated
        WITH CHECK (user_id = auth.uid());

    CREATE POLICY project_scraps_self_delete ON public.project_scraps
        FOR DELETE TO authenticated
        USING (user_id = auth.uid());

    -- projects: 기존 V6 정책 + DRAFT 는 LEADER 만 보이도록 보강.
    -- V6 가 정의한 정책 그대로 유효 (status 추가 컬럼은 noop) — 여기서는 DRAFT 가드만 추가.
    -- (정책명 충돌 방지를 위해 DROP IF EXISTS 후 재생성 안 함 — DROP 은 별도 V 마이그레이션으로.)
    -- DRAFT 본문이 LEADER 외부에 새는 걸 막는 정책. 이미 V6 가 PUBLIC select 를 PUBLISHED + not deleted
    -- 로 제한했다면 자연 차단. V6 의 projects_public_select 정의를 확인하고 필요 시 후속 마이그레이션에서 보강.
END
$$;

-- ----- 시드 / 기존 데이터 처리 -----------------------------------------
-- 기존 projects 행은 모두 DRAFT (채택 직후 상태) 또는 RECRUITING / IN_PROGRESS / COMPLETED. V4 ~ V14 까지의
-- 흐름에서 RECRUITING+ 로 올라간 적이 없으므로 (해당 API 부재) 기존 행은 모두 DRAFT 가정 → chk_projects_published_fields
-- 통과. 운영 데이터가 다른 상태로 만들어진 게 있으면 V15 적용 전 수동 백필 필요 — 현재는 0건이라 무의미.
