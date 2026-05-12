-- =====================================================================
-- Dev 시드 데이터
--
-- 실행 방법: Supabase Dashboard → SQL Editor → New query → 붙여넣기 → Run
-- (Flyway 마이그레이션 아님 — 운영 환경에 자동 적용되면 안 되므로 분리)
--
-- 전제: V1~V6 적용 완료. auth.users 에 아래 두 UUID 의 사용자 존재 (handle_new_user 트리거가
-- public.users / user_credits 까지 생성한 상태).
--
-- 재실행 시: 본 스크립트는 멱등성이 없으므로 같은 시드를 반복 INSERT 하면 ID 충돌 또는
-- 중복 row 가 쌓인다. 다시 시드하려면 먼저 정리 (마지막 주석 참조).
-- =====================================================================

DO $$
DECLARE
    v_main_user uuid := 'bf7f871b-aacd-474d-90b6-e566282329bb';
    v_seed_user uuid := '07d216a5-e186-4eb4-bd20-fb92e2bb91f9';
    v_zero_vec  vector(1536) := array_fill(0::float4, ARRAY[1536])::vector(1536);
    v_idea_id   bigint;
    v_doc_id    bigint;
    v_tx_id     bigint;
    v_reward_tx bigint;
    v_main_bal  bigint;
    v_seed_bal  bigint;

    -- 구매·채택 흐름을 거칠 두 idea 의 id 를 보관 (PL/pgSQL 변수)
    v_idea_1 bigint;
    v_doc_1  bigint;
    v_idea_2 bigint;
    v_doc_2  bigint;
BEGIN
    -- ===== 0. 초기 잔액 그랜트 (main_user 500) =====
    INSERT INTO credit_transactions
        (user_id, amount, type, balance_after, reference_type, reference_id, description)
    VALUES
        (v_main_user, 500, 'ADJUST', 500, 'SEED', 'main-initial', '시드 초기 잔액');
    UPDATE user_credits SET balance = 500, updated_at = now() WHERE user_id = v_main_user;
    v_main_bal := 500;
    v_seed_bal := 0;

    -- ===== 1. seed_user 작성 ideas (5개, PUBLISHED) =====

    -- Idea A: 구매·채택 대상 #1
    INSERT INTO ideas (author_id, status, price_credits, reward_credits)
    VALUES (v_seed_user, 'PUBLISHED', 100, 50) RETURNING id INTO v_idea_id;
    INSERT INTO idea_documents (idea_id, version, title, content_md) VALUES
        (v_idea_id, 1, '1인 가구 식단 큐레이션',
         E'# Problem\n혼자 먹기엔 양 조절·재료 활용이 어렵다.\n\n# Solution\n1인분 기준 정량 레시피 + 마트 장보기 리스트를 자동 생성.')
        RETURNING id INTO v_doc_id;
    UPDATE ideas SET current_version_id = v_doc_id WHERE id = v_idea_id;
    INSERT INTO idea_embeddings (idea_id, embedding, keywords)
    VALUES (v_idea_id, v_zero_vec, ARRAY['1인가구', '식단', '큐레이션', '마트', '장보기']);
    v_idea_1 := v_idea_id;
    v_doc_1  := v_doc_id;

    -- Idea B: 구매·채택 대상 #2
    INSERT INTO ideas (author_id, status, price_credits, reward_credits)
    VALUES (v_seed_user, 'PUBLISHED', 100, 50) RETURNING id INTO v_idea_id;
    INSERT INTO idea_documents (idea_id, version, title, content_md) VALUES
        (v_idea_id, 1, '동네 빵집 도장깨기',
         E'# Problem\n동네 빵집을 한눈에 모아 보기 어렵다.\n\n# Solution\n위치 기반으로 매일 한 곳을 추천하고 후기를 누적.')
        RETURNING id INTO v_doc_id;
    UPDATE ideas SET current_version_id = v_doc_id WHERE id = v_idea_id;
    INSERT INTO idea_embeddings (idea_id, embedding, keywords)
    VALUES (v_idea_id, v_zero_vec, ARRAY['빵집', '동네', '위치기반', '추천']);
    v_idea_2 := v_idea_id;
    v_doc_2  := v_doc_id;

    -- Idea C: 미구매 default 카드
    INSERT INTO ideas (author_id, status, price_credits, reward_credits)
    VALUES (v_seed_user, 'PUBLISHED', 80, 40) RETURNING id INTO v_idea_id;
    INSERT INTO idea_documents (idea_id, version, title, content_md) VALUES
        (v_idea_id, 1, '반려동물 산책 동선 자동 기록',
         E'# Problem\n견주끼리 산책로 공유가 비효율.\n\n# Solution\nGPS 자동 기록 + 동네 견주와 동선/시간대 공유.')
        RETURNING id INTO v_doc_id;
    UPDATE ideas SET current_version_id = v_doc_id WHERE id = v_idea_id;
    INSERT INTO idea_embeddings (idea_id, embedding, keywords)
    VALUES (v_idea_id, v_zero_vec, ARRAY['반려동물', '산책', 'GPS', '위치공유', '동네']);

    -- Idea D
    INSERT INTO ideas (author_id, status, price_credits, reward_credits)
    VALUES (v_seed_user, 'PUBLISHED', 120, 60) RETURNING id INTO v_idea_id;
    INSERT INTO idea_documents (idea_id, version, title, content_md) VALUES
        (v_idea_id, 1, '프리랜서 세금 자동 계산 SaaS',
         E'# Problem\n분기별 세금 예상 계산이 번거롭다.\n\n# Solution\n카드/은행 API 연동 → 자동 분류 → 부가세·종소세 예상치 알림.')
        RETURNING id INTO v_doc_id;
    UPDATE ideas SET current_version_id = v_doc_id WHERE id = v_idea_id;
    INSERT INTO idea_embeddings (idea_id, embedding, keywords)
    VALUES (v_idea_id, v_zero_vec, ARRAY['프리랜서', '세금', '자동화', 'SaaS']);

    -- Idea E
    INSERT INTO ideas (author_id, status, price_credits, reward_credits)
    VALUES (v_seed_user, 'PUBLISHED', 90, 45) RETURNING id INTO v_idea_id;
    INSERT INTO idea_documents (idea_id, version, title, content_md) VALUES
        (v_idea_id, 1, '오프라인 모임 사진 자동 공유 갤러리',
         E'# Problem\n모임 끝나고 사진 모으기 번거롭다.\n\n# Solution\nQR 스캔으로 같은 시간·장소 사진을 자동 묶음.')
        RETURNING id INTO v_doc_id;
    UPDATE ideas SET current_version_id = v_doc_id WHERE id = v_idea_id;
    INSERT INTO idea_embeddings (idea_id, embedding, keywords)
    VALUES (v_idea_id, v_zero_vec, ARRAY['모임', '사진', '공유', '갤러리', 'QR']);

    -- ===== 2. main_user 작성 ideas (2개, PUBLISHED) =====

    INSERT INTO ideas (author_id, status, price_credits, reward_credits)
    VALUES (v_main_user, 'PUBLISHED', 100, 50) RETURNING id INTO v_idea_id;
    INSERT INTO idea_documents (idea_id, version, title, content_md) VALUES
        (v_idea_id, 1, '독서 모임 페이스 메이커',
         E'# Problem\n온라인 독서모임은 진도 맞추기 어렵다.\n\n# Solution\n책 페이지·챕터 기준 자동 일정 + 진도율 시각화.')
        RETURNING id INTO v_doc_id;
    UPDATE ideas SET current_version_id = v_doc_id WHERE id = v_idea_id;
    INSERT INTO idea_embeddings (idea_id, embedding, keywords)
    VALUES (v_idea_id, v_zero_vec, ARRAY['독서모임', '페이스메이커', '진도', '생산성']);

    INSERT INTO ideas (author_id, status, price_credits, reward_credits)
    VALUES (v_main_user, 'PUBLISHED', 110, 55) RETURNING id INTO v_idea_id;
    INSERT INTO idea_documents (idea_id, version, title, content_md) VALUES
        (v_idea_id, 1, '헬스장 PT 기록 노트',
         E'# Problem\n트레이너·회원 간 운동/식단 기록 분리.\n\n# Solution\n캘린더+체중 그래프+메모를 한 화면에서 공유 편집.')
        RETURNING id INTO v_doc_id;
    UPDATE ideas SET current_version_id = v_doc_id WHERE id = v_idea_id;
    INSERT INTO idea_embeddings (idea_id, embedding, keywords)
    VALUES (v_idea_id, v_zero_vec, ARRAY['헬스장', 'PT', '기록', '트레이너', '공유']);

    -- ===== 3. main_user 가 Idea A, B 구매 (§8.2) =====

    -- Purchase Idea A (-100)
    v_main_bal := v_main_bal - 100;
    INSERT INTO credit_transactions
        (user_id, amount, type, balance_after, reference_type, reference_id, description)
    VALUES (v_main_user, -100, 'SPEND', v_main_bal, 'IDEA_PURCHASE', v_idea_1::text, '아이디어 구매')
    RETURNING id INTO v_tx_id;
    UPDATE user_credits SET balance = v_main_bal, updated_at = now() WHERE user_id = v_main_user;
    INSERT INTO idea_purchases (idea_id, buyer_id, document_id, transaction_id)
    VALUES (v_idea_1, v_main_user, v_doc_1, v_tx_id);

    -- Purchase Idea B (-100)
    v_main_bal := v_main_bal - 100;
    INSERT INTO credit_transactions
        (user_id, amount, type, balance_after, reference_type, reference_id, description)
    VALUES (v_main_user, -100, 'SPEND', v_main_bal, 'IDEA_PURCHASE', v_idea_2::text, '아이디어 구매')
    RETURNING id INTO v_tx_id;
    UPDATE user_credits SET balance = v_main_bal, updated_at = now() WHERE user_id = v_main_user;
    INSERT INTO idea_purchases (idea_id, buyer_id, document_id, transaction_id)
    VALUES (v_idea_2, v_main_user, v_doc_2, v_tx_id);

    -- ===== 4. main_user 가 Idea A, B 채택 → projects + rewards (§8.3) =====

    -- Adopt Idea A → project + reward to seed_user (+50)
    INSERT INTO projects (idea_id, leader_id, status, idea_snapshot_md)
    SELECT v_idea_1, v_main_user, 'RECRUITING', content_md
    FROM idea_documents WHERE id = v_doc_1 RETURNING id INTO v_idea_id;
    INSERT INTO project_members (project_id, user_id, role)
    VALUES (v_idea_id, v_main_user, 'LEADER');

    v_seed_bal := v_seed_bal + 50;
    INSERT INTO credit_transactions
        (user_id, amount, type, balance_after, reference_type, reference_id, description)
    VALUES (v_seed_user, 50, 'REWARD', v_seed_bal, 'ADOPTION', v_idea_1::text, '채택 보상')
    RETURNING id INTO v_reward_tx;
    UPDATE user_credits SET balance = v_seed_bal, updated_at = now() WHERE user_id = v_seed_user;
    INSERT INTO rewards
        (recipient_user_id, reward_type, amount, status, transaction_id, idea_id, paid_at)
    VALUES (v_seed_user, 'ADOPTION', 50, 'PAID', v_reward_tx, v_idea_1, now());

    -- Adopt Idea B → project + reward to seed_user (+50)
    INSERT INTO projects (idea_id, leader_id, status, idea_snapshot_md)
    SELECT v_idea_2, v_main_user, 'IN_PROGRESS', content_md
    FROM idea_documents WHERE id = v_doc_2 RETURNING id INTO v_idea_id;
    INSERT INTO project_members (project_id, user_id, role)
    VALUES (v_idea_id, v_main_user, 'LEADER');

    v_seed_bal := v_seed_bal + 50;
    INSERT INTO credit_transactions
        (user_id, amount, type, balance_after, reference_type, reference_id, description)
    VALUES (v_seed_user, 50, 'REWARD', v_seed_bal, 'ADOPTION', v_idea_2::text, '채택 보상')
    RETURNING id INTO v_reward_tx;
    UPDATE user_credits SET balance = v_seed_bal, updated_at = now() WHERE user_id = v_seed_user;
    INSERT INTO rewards
        (recipient_user_id, reward_type, amount, status, transaction_id, idea_id, paid_at)
    VALUES (v_seed_user, 'ADOPTION', 50, 'PAID', v_reward_tx, v_idea_2, now());

    RAISE NOTICE '시드 완료. main_balance=%, seed_balance=%', v_main_bal, v_seed_bal;
END
$$;

-- =====================================================================
-- 정리 (다시 시드하고 싶을 때만 — FK 의존순으로 삭제)
--
-- BEGIN;
-- DELETE FROM rewards WHERE reward_type = 'ADOPTION';
-- DELETE FROM project_members WHERE user_id IN (
--   'bf7f871b-aacd-474d-90b6-e566282329bb', '07d216a5-e186-4eb4-bd20-fb92e2bb91f9');
-- DELETE FROM projects WHERE leader_id = 'bf7f871b-aacd-474d-90b6-e566282329bb';
-- DELETE FROM idea_purchases WHERE buyer_id = 'bf7f871b-aacd-474d-90b6-e566282329bb';
-- DELETE FROM idea_embeddings WHERE idea_id IN (SELECT id FROM ideas WHERE author_id IN (
--   'bf7f871b-aacd-474d-90b6-e566282329bb', '07d216a5-e186-4eb4-bd20-fb92e2bb91f9'));
-- -- ideas.current_version_id → idea_documents.id 순환 FK 끊고 삭제
-- UPDATE ideas SET current_version_id = NULL WHERE author_id IN (
--   'bf7f871b-aacd-474d-90b6-e566282329bb', '07d216a5-e186-4eb4-bd20-fb92e2bb91f9');
-- DELETE FROM idea_documents WHERE idea_id IN (SELECT id FROM ideas WHERE author_id IN (
--   'bf7f871b-aacd-474d-90b6-e566282329bb', '07d216a5-e186-4eb4-bd20-fb92e2bb91f9'));
-- DELETE FROM ideas WHERE author_id IN (
--   'bf7f871b-aacd-474d-90b6-e566282329bb', '07d216a5-e186-4eb4-bd20-fb92e2bb91f9');
-- -- credit_transactions 은 append-only — 시드 정리 후엔 balance 캐시도 0 으로
-- DELETE FROM credit_transactions WHERE reference_type IN ('SEED', 'IDEA_PURCHASE', 'ADOPTION')
--   AND user_id IN ('bf7f871b-aacd-474d-90b6-e566282329bb', '07d216a5-e186-4eb4-bd20-fb92e2bb91f9');
-- -- ↑ 트리거 block_credit_tx_modification 이 차단. service_role 도 차단. 정리하려면 트리거 임시 disable 필요.
-- UPDATE user_credits SET balance = 0 WHERE user_id IN (
--   'bf7f871b-aacd-474d-90b6-e566282329bb', '07d216a5-e186-4eb4-bd20-fb92e2bb91f9');
-- COMMIT;
-- =====================================================================
