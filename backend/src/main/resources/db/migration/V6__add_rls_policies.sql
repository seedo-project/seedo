-- =====================================================================
-- V6: Supabase RLS 정책 (현존 테이블 — V1~V4)
-- 참고: 루트 CLAUDE.md §7 (RLS 정책 패턴)
-- 의존: V1 (users/RBAC/credit), V2 (ideas/*), V3 (auth.users sync), V4 (projects/rewards)
--
-- 적용 범위:
--   - V3 와 동일 패턴 — auth 스키마 (= Supabase 환경) 에서만 활성화.
--   - 로컬 Testcontainers PG 에는 auth.uid() 가 없으므로 본문 전체 noop.
--   - Spring 은 service_role 키로 접속 → RLS 우회 (Supabase 기본 동작).
--   - 따라서 RLS 가 적용돼도 기존 IT / 서비스 트랜잭션은 영향 없음.
--
-- 미포함 (해당 테이블이 V6 시점에 아직 없음 — 도메인 추가 시 같이 정책 부착):
--   posts, post_applications, *_comments, hypes, project_follows, notifications
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth') THEN
        RETURN;
    END IF;

    -- ============== 1. users — 본인 SELECT/UPDATE ====================
    -- INSERT/DELETE 정책 없음 — handle_new_user() (V3, SECURITY DEFINER) 와 ADMIN 작업은 service_role.
    ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
    CREATE POLICY users_self_select ON public.users
        FOR SELECT TO authenticated
        USING (id = auth.uid());
    CREATE POLICY users_self_update ON public.users
        FOR UPDATE TO authenticated
        USING (id = auth.uid())
        WITH CHECK (id = auth.uid());

    -- ============== 2. RBAC 참조 테이블 — 공개 SELECT ================
    -- roles / permissions / role_permissions: UI 에서 권한명 표기에 필요할 수 있어 공개 SELECT.
    -- write 는 service_role (시드 + ADMIN 작업) 전용.
    ALTER TABLE public.roles            ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.permissions      ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.role_permissions ENABLE ROW LEVEL SECURITY;
    CREATE POLICY roles_public_select            ON public.roles            FOR SELECT TO public USING (true);
    CREATE POLICY permissions_public_select      ON public.permissions      FOR SELECT TO public USING (true);
    CREATE POLICY role_permissions_public_select ON public.role_permissions FOR SELECT TO public USING (true);

    -- user_roles: 본인 매핑만 SELECT. write 는 service_role (handle_new_user / ADMIN).
    ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;
    CREATE POLICY user_roles_self_select ON public.user_roles
        FOR SELECT TO authenticated
        USING (user_id = auth.uid());

    -- ============== 3. 크레딧 — 본인 SELECT ==========================
    -- 잔액·원장은 본인만. 모든 write 는 Spring §8 트랜잭션 전용 (append-only 트리거가 V1 에 있음).
    ALTER TABLE public.user_credits        ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.credit_transactions ENABLE ROW LEVEL SECURITY;
    CREATE POLICY user_credits_self_select ON public.user_credits
        FOR SELECT TO authenticated
        USING (user_id = auth.uid());
    CREATE POLICY credit_tx_self_select ON public.credit_transactions
        FOR SELECT TO authenticated
        USING (user_id = auth.uid());

    -- ============== 4. ideas — 공개 피드 + 본인 작성 =================
    -- SELECT: PUBLISHED & not-deleted 공개  OR  본인 작성건 전체 (DRAFT 포함).
    -- INSERT/UPDATE: author_id = auth.uid().
    -- hard DELETE 정책 없음 — 소프트 삭제 우선 (CLAUDE.md §6.4), hard delete 는 ADMIN/service_role.
    ALTER TABLE public.ideas ENABLE ROW LEVEL SECURITY;
    CREATE POLICY ideas_public_select ON public.ideas
        FOR SELECT TO public
        USING (status = 'PUBLISHED' AND deleted_at IS NULL);
    CREATE POLICY ideas_author_select ON public.ideas
        FOR SELECT TO authenticated
        USING (author_id = auth.uid());
    CREATE POLICY ideas_author_insert ON public.ideas
        FOR INSERT TO authenticated
        WITH CHECK (author_id = auth.uid());
    CREATE POLICY ideas_author_update ON public.ideas
        FOR UPDATE TO authenticated
        USING (author_id = auth.uid())
        WITH CHECK (author_id = auth.uid());

    -- ============== 5. idea_documents — 구매자/작성자만 본문 =========
    -- 정책 패턴 CLAUDE.md §7. write 는 Spring §8.4 finalize / 새 버전 전용.
    ALTER TABLE public.idea_documents ENABLE ROW LEVEL SECURITY;
    CREATE POLICY idea_documents_buyer_or_author_select ON public.idea_documents
        FOR SELECT TO authenticated
        USING (
            EXISTS (
                SELECT 1 FROM public.idea_purchases ip
                WHERE ip.idea_id = idea_documents.idea_id
                  AND ip.buyer_id = auth.uid()
            )
            OR EXISTS (
                SELECT 1 FROM public.ideas i
                WHERE i.id = idea_documents.idea_id
                  AND i.author_id = auth.uid()
            )
        );

    -- ============== 6. idea_chat_sessions / messages — 본인만 ========
    -- 챗봇 대화는 작성자 본인의 사적 흐름. session 본인 SELECT/INSERT/UPDATE.
    -- finalize 후 메타 갱신은 Spring §8.4 (service_role).
    ALTER TABLE public.idea_chat_sessions ENABLE ROW LEVEL SECURITY;
    CREATE POLICY chat_sessions_self_select ON public.idea_chat_sessions
        FOR SELECT TO authenticated
        USING (user_id = auth.uid());
    CREATE POLICY chat_sessions_self_insert ON public.idea_chat_sessions
        FOR INSERT TO authenticated
        WITH CHECK (user_id = auth.uid());
    CREATE POLICY chat_sessions_self_update ON public.idea_chat_sessions
        FOR UPDATE TO authenticated
        USING (user_id = auth.uid())
        WITH CHECK (user_id = auth.uid());

    -- messages 는 session 소유자만 SELECT/INSERT. delete 는 session CASCADE.
    ALTER TABLE public.idea_chat_messages ENABLE ROW LEVEL SECURITY;
    CREATE POLICY chat_messages_session_owner_select ON public.idea_chat_messages
        FOR SELECT TO authenticated
        USING (EXISTS (
            SELECT 1 FROM public.idea_chat_sessions s
            WHERE s.id = idea_chat_messages.session_id
              AND s.user_id = auth.uid()
        ));
    CREATE POLICY chat_messages_session_owner_insert ON public.idea_chat_messages
        FOR INSERT TO authenticated
        WITH CHECK (EXISTS (
            SELECT 1 FROM public.idea_chat_sessions s
            WHERE s.id = idea_chat_messages.session_id
              AND s.user_id = auth.uid()
        ));

    -- ============== 7. idea_embeddings — 공개 SELECT =================
    -- 검색 (RAG, 키워드 매칭) 에서 공개 노출돼야 함. write 는 service_role 전용.
    -- 임베딩 자체로는 본문 복원 불가능 — 공개 노출 위험 낮음.
    ALTER TABLE public.idea_embeddings ENABLE ROW LEVEL SECURITY;
    CREATE POLICY idea_embeddings_public_select ON public.idea_embeddings
        FOR SELECT TO public
        USING (true);

    -- ============== 8. idea_purchases — 본인 buyer SELECT ============
    -- write 는 Spring §8.2 구매 트랜잭션 전용 (UNIQUE + 본인구매 차단 트리거가 V2 에 있음).
    ALTER TABLE public.idea_purchases ENABLE ROW LEVEL SECURITY;
    CREATE POLICY idea_purchases_buyer_select ON public.idea_purchases
        FOR SELECT TO authenticated
        USING (buyer_id = auth.uid());

    -- ============== 9. projects — 공개 SELECT + leader UPDATE ========
    -- DELETED 제외 모두 공개 (피드/상세). INSERT 는 Spring §8.3 채택 트랜잭션 전용.
    ALTER TABLE public.projects ENABLE ROW LEVEL SECURITY;
    CREATE POLICY projects_public_select ON public.projects
        FOR SELECT TO public
        USING (status <> 'DELETED' AND deleted_at IS NULL);
    CREATE POLICY projects_leader_update ON public.projects
        FOR UPDATE TO authenticated
        USING (leader_id = auth.uid())
        WITH CHECK (leader_id = auth.uid());

    -- ============== 10. project_members — 공개 SELECT ================
    -- 멤버 리스트는 프로젝트 페이지에서 공개. write 는 Spring (§8.3 LEADER 자동 추가, 추후 가입 정책).
    ALTER TABLE public.project_members ENABLE ROW LEVEL SECURITY;
    CREATE POLICY project_members_public_select ON public.project_members
        FOR SELECT TO public
        USING (true);

    -- ============== 11. rewards — 수령자 본인 SELECT =================
    -- 보상 원장은 사적 정보. write 는 Spring §8.3 채택 트랜잭션 전용.
    ALTER TABLE public.rewards ENABLE ROW LEVEL SECURITY;
    CREATE POLICY rewards_recipient_select ON public.rewards
        FOR SELECT TO authenticated
        USING (recipient_user_id = auth.uid());
END
$$;
