package dev.seedo.auth;

import dev.seedo.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V3 (`add_supabase_auth_sync`) 는 `auth` 스키마 존재 시에만 FK / 함수 / 트리거를 만든다.
 * Testcontainers PG 에는 `auth` 스키마가 없으므로 V3 는 noop 으로 통과해야 한다 — 즉
 *   - 마이그레이션 자체는 SUCCESS (flyway_schema_history 에 V3 row, success=true)
 *   - 부수 객체 (handle_new_user 함수, on_auth_user_created 트리거, fk_users_auth_id FK) 는
 *     하나도 만들어지지 않음.
 *
 * Supabase 환경에서의 실제 동작 (가입 → public.users/user_roles/user_credits 자동 생성) 은
 * 자동화 IT 범위 외 — 수동 검증으로 처리 (이슈 #65 본문).
 */
class SupabaseAuthTriggerIT extends AbstractIntegrationTest {

    @PersistenceContext
    private EntityManager em;

    @Test
    void v3_marked_success_in_flyway_history() {
        assertThat(exists(
                "SELECT 1 FROM flyway_schema_history " +
                "WHERE version = '3' AND success = true"
        )).as("V3 must be applied successfully").isTrue();
    }

    @Test
    void v8_marked_success_in_flyway_history() {
        // V8 도 auth 스키마 가드 — Testcontainers 에선 함수 본문 갱신이 일어나지 않지만
        // 마이그레이션 자체는 성공 처리되어야 한다.
        assertThat(exists(
                "SELECT 1 FROM flyway_schema_history " +
                "WHERE version = '8' AND success = true"
        )).as("V8 must be applied successfully").isTrue();
    }

    @Test
    void auth_schema_absent_on_testcontainers() {
        assertThat(exists(
                "SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth'"
        )).as("Testcontainers PG 에는 auth 스키마가 없어야 V3 가 noop 임이 의미가 있음").isFalse();
    }

    @Test
    void handle_new_user_function_not_created() {
        assertThat(exists(
                "SELECT 1 FROM pg_proc " +
                "WHERE proname = 'handle_new_user' " +
                "  AND pronamespace = 'public'::regnamespace"
        )).isFalse();
    }

    @Test
    void on_auth_user_created_trigger_not_created() {
        assertThat(exists(
                "SELECT 1 FROM pg_trigger WHERE tgname = 'on_auth_user_created'"
        )).isFalse();
    }

    @Test
    void fk_users_auth_id_not_created() {
        assertThat(exists(
                "SELECT 1 FROM pg_constraint WHERE conname = 'fk_users_auth_id'"
        )).isFalse();
    }

    @SuppressWarnings("rawtypes")
    private boolean exists(String sql) {
        return !em.createNativeQuery(sql).getResultList().isEmpty();
    }
}
