-- =====================================================================
-- V4: project 도메인 + rewards
-- 참고: 루트 CLAUDE.md §5.4, §5.5, §8.3, §12 (정책 — 한 아이디어 → 여러 프로젝트, 보상은 첫 채택자만)
-- 의존: V1 (users, credit_transactions, set_updated_at()), V2 (ideas)
-- =====================================================================


-- ----- 1. projects --------------------------------------------------
-- 채택 시 만들어진다 (§8.3). idea_snapshot_md 는 채택 시점 idea_documents.content_md 의 복사본 —
-- 이후 idea 가 새 버전을 쌓아도 프로젝트의 출발 문서는 보존된다 (§6.5 산 시점 스냅샷과 동일 사상).
--
-- idea / leader hard delete 는 RESTRICT — 아이디어·작성자가 사라지면 프로젝트 정합성이 깨진다.
-- 사용자/아이디어가 archive 또는 soft delete 가 정상 경로. ADMIN 정리 시 명시적으로 떼낸 뒤만 hard delete.
--
-- ARCHIVED 와 DELETED 분리 (idea 와 동일 패턴, §6.4): archive 는 작성자 의도, delete 는 사고/관리.
-- ARCHIVED 인데 deleted_at NOT NULL 이거나, DELETED 인데 deleted_at NULL 인 row 는 CHECK 가 차단.

CREATE TABLE projects (
    id               bigserial PRIMARY KEY,
    idea_id          bigint      NOT NULL REFERENCES ideas(id) ON DELETE RESTRICT,
    leader_id        uuid        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status           varchar(20) NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT', 'RECRUITING', 'IN_PROGRESS', 'COMPLETED', 'ARCHIVED', 'DELETED')),
    idea_snapshot_md text        NOT NULL,
    deleted_at       timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    -- 소프트삭제 양방향 정합성 — idea / users 와 동일 패턴 (§6.4).
    CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);

CREATE INDEX idx_projects_idea
    ON projects(idea_id);
CREATE INDEX idx_projects_leader
    ON projects(leader_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_projects_status_created
    ON projects(status, created_at DESC) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_projects_updated_at
BEFORE UPDATE ON projects
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ----- 2. project_members -------------------------------------------
-- project CASCADE: 프로젝트가 hard delete 되면 멤버십도 같이 사라진다 (멤버십은 프로젝트 종속 derived data, §5.8).
-- user RESTRICT: 사용자는 archive/soft delete 우선. 활성 멤버십이 있는 채로 hard delete 되는 것 차단.
--
-- 활성 멤버십 유일성: (project_id, user_id) 가 left_at IS NULL 인 row 중에서만 UNIQUE.
-- 재가입 시나리오 (탈퇴 후 재가입) 는 left_at NOT NULL 인 옛 row 를 보존한 채 새 row INSERT 가능.

CREATE TABLE project_members (
    id         bigserial PRIMARY KEY,
    project_id bigint      NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users(id)    ON DELETE RESTRICT,
    role       varchar(10) NOT NULL CHECK (role IN ('LEADER', 'MEMBER')),
    joined_at  timestamptz NOT NULL DEFAULT now(),
    left_at    timestamptz
);

CREATE UNIQUE INDEX project_members_active_uniq
    ON project_members(project_id, user_id) WHERE left_at IS NULL;

CREATE INDEX idx_project_members_user_active
    ON project_members(user_id) WHERE left_at IS NULL;


-- ----- 3. rewards ---------------------------------------------------
-- recipient RESTRICT: 보상 받은 사용자가 hard delete 되면 원장 정합성이 깨진다. archive/soft delete 우선.
-- transaction_id RESTRICT: credit_transactions 는 append-only 라 hard delete 불가능 — 그래도 명시.
-- idea_id RESTRICT: ADOPTION reward 가 idea 와 1:1. idea hard delete 는 보상 정산 후에만 (§5.8).
--
-- 정책 (§12): "한 아이디어 → 여러 프로젝트 허용, 보상은 첫 채택자만". rewards.idea_id partial UNIQUE 가 강제.
-- ADOPTION 타입만 idea_id NOT NULL, 다른 타입(INTERVIEW/ADMIN/OTHER)은 idea_id NULL.
--
-- MVP 는 인스턴트 지급 — 채택 트랜잭션 안에서 PENDING 없이 곧장 PAID 로 INSERT.
-- PENDING 상태는 추후 결재 흐름이 들어오면 사용 (예: 인터뷰 보상 사전 약정).

CREATE TABLE rewards (
    id                bigserial PRIMARY KEY,
    recipient_user_id uuid        NOT NULL REFERENCES users(id)               ON DELETE RESTRICT,
    reward_type       varchar(20) NOT NULL
                      CHECK (reward_type IN ('ADOPTION', 'INTERVIEW', 'ADMIN', 'OTHER')),
    amount            int         NOT NULL CHECK (amount >= 0),
    status            varchar(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'PAID')),
    transaction_id    bigint               REFERENCES credit_transactions(id) ON DELETE RESTRICT,
    idea_id           bigint               REFERENCES ideas(id)               ON DELETE RESTRICT,
    paid_at           timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    -- ADOPTION ↔ idea_id 양방향: ADOPTION 만 idea_id NOT NULL, 그 외 타입은 idea_id NULL 강제.
    CHECK ((reward_type = 'ADOPTION') = (idea_id IS NOT NULL)),
    -- PAID ↔ transaction_id / paid_at 양방향: PAID 가 되려면 원장 행 + 지급 시각이 박혀야 한다.
    CHECK ((status = 'PAID') = (transaction_id IS NOT NULL)),
    CHECK ((status = 'PAID') = (paid_at        IS NOT NULL))
);

-- §12 정책의 DB 가드 — 같은 idea 의 ADOPTION reward 는 단 1 행.
-- 두 번째 채택자가 service 사전 체크를 race 로 빠져나가도 여기서 23505 로 잡힌다.
CREATE UNIQUE INDEX rewards_adoption_idea_uniq
    ON rewards(idea_id) WHERE reward_type = 'ADOPTION';

CREATE INDEX idx_rewards_recipient
    ON rewards(recipient_user_id, created_at DESC);
