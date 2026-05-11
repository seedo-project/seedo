-- =====================================================================
-- V5: 프로젝트 / 보상 정합성 추가 가드 (CodeRabbit PR #82)
-- 참고: 루트 CLAUDE.md §5.4, §5.5, §8.3
-- 의존: V4 (projects, project_members, rewards)
-- =====================================================================
-- V4 의 partial UNIQUE 들은 다음을 막지 못한다:
--   1. 한 프로젝트에 활성 LEADER 가 둘 이상 생기는 상태 (projects.leader_id 와 어긋남)
--   2. 같은 credit_transactions 행이 두 rewards 에 박혀 정산이 중복되는 상태
-- 도메인 메서드 가드가 1 차 방어선이지만, 직접 SQL 조작 / 향후 멱등성 SKIP 경로 미처리 등
-- 사고 경로를 DB 가 마지막에 잡도록 partial UNIQUE 두 개를 추가한다.


-- ----- 1. project_members: 프로젝트당 활성 LEADER 1 명 ---------------
-- V4 의 (project_id, user_id) UNIQUE 는 같은 사용자의 중복 활성 멤버십만 차단 — 다른 두 사용자가
-- 동시에 LEADER 인 상태는 허용된다. projects.leader_id 는 한 행이므로 멤버십 쪽도 1 명만 활성이어야
-- aggregate 정합성이 유지된다. 향후 리더 이관 메서드는 (구) LEADER 를 leave 후 (신) LEADER 를 INSERT
-- 한 트랜잭션으로 — 그 시점에 이 인덱스가 race 사고를 잡는다.

CREATE UNIQUE INDEX project_members_active_leader_uniq
    ON project_members(project_id) WHERE role = 'LEADER' AND left_at IS NULL;


-- ----- 2. rewards: credit_transactions 1:1 --------------------------
-- V4 의 CHECK 는 PAID 일 때 transaction_id NOT NULL 만 보장. 같은 transaction_id 가 여러 rewards 에
-- 박히는 경로는 막지 못한다 — ChargeCreditService.charge 가 idempotentSkip=true 로 같은 transactionId
-- 를 재반환했을 때 호출자가 분기를 무시하면 같은 원장 행이 두 rewards 에 박힐 수 있다.
--
-- credit_transactions 의 (reference_type, reference_id) UNIQUE 가 멱등성을 보장하더라도,
-- rewards 쪽에도 1:1 강제를 두면 정산 추적이 끊어지는 사고를 또 한 번 막을 수 있다.

CREATE UNIQUE INDEX rewards_transaction_id_uniq
    ON rewards(transaction_id) WHERE transaction_id IS NOT NULL;
