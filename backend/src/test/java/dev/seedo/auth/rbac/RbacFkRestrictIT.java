package dev.seedo.auth.rbac;

import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static dev.seedo.support.AbstractIntegrationTest.SQLSTATE_FOREIGN_KEY_VIOLATION;
import static dev.seedo.support.AbstractIntegrationTest.assertSqlState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V1 §5.8 / 코드리뷰 2차: RBAC 매핑 FK 는 모두 ON DELETE RESTRICT.
 * users / roles / permissions hard delete 시 매핑이 조용히 사라지는 것을 차단한다.
 * 매핑을 먼저 명시적으로 정리해야만 부모 삭제 가능.
 */
@Transactional
class RbacFkRestrictIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void delete_role_referenced_by_role_permissions_blocked() {
        // V1 시드: roles(id=1, code='USER') + role_permissions 매핑 10개
        assertThatThrownBy(() ->
                em.createNativeQuery("DELETE FROM roles WHERE id = 1").executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_FOREIGN_KEY_VIOLATION));
    }

    @Test
    void delete_permission_referenced_by_role_permissions_blocked() {
        // V1 시드: permissions(code='IDEA_CREATE') + role_permissions 매핑
        assertThatThrownBy(() ->
                em.createNativeQuery("DELETE FROM permissions WHERE code = 'IDEA_CREATE'").executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_FOREIGN_KEY_VIOLATION));
    }

    @Test
    void delete_user_referenced_by_user_roles_blocked() {
        UUID uid = UserFixture.createWithRole(userRepo, em, 1L);

        assertThatThrownBy(() ->
                em.createNativeQuery("DELETE FROM users WHERE id = CAST(:uid AS uuid)")
                        .setParameter("uid", uid.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_FOREIGN_KEY_VIOLATION));
    }

    @Test
    void delete_user_after_clearing_user_roles_succeeds() {
        UUID uid = UserFixture.createWithRole(userRepo, em, 1L);

        em.createNativeQuery("DELETE FROM user_roles WHERE user_id = CAST(:uid AS uuid)")
                .setParameter("uid", uid.toString())
                .executeUpdate();

        int affected = em.createNativeQuery("DELETE FROM users WHERE id = CAST(:uid AS uuid)")
                .setParameter("uid", uid.toString())
                .executeUpdate();

        assertThat(affected).isEqualTo(1);
    }

}
