package dev.seedo.user;

import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.user.domain.User;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static dev.seedo.support.AbstractIntegrationTest.SQLSTATE_CHECK_VIOLATION;
import static dev.seedo.support.AbstractIntegrationTest.assertSqlState;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V1: status='DELETED' ↔ deleted_at IS NOT NULL 양방향 정합성.
 * 두 컬럼 한쪽만 박힌 row 는 CHECK 가 차단.
 */
@Transactional
class UserSoftDeleteInvariantIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void deleted_status_without_deleted_at_blocked() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO users(id, email, nickname, status) " +
                                        "VALUES (CAST(:id AS uuid), :email, :nick, 'DELETED')")
                        .setParameter("id", id.toString())
                        .setParameter("email", "u-" + id + "@test")
                        .setParameter("nick", nick(id))
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void active_with_deleted_at_blocked() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO users(id, email, nickname, status, deleted_at) " +
                                        "VALUES (CAST(:id AS uuid), :email, :nick, 'ACTIVE', now())")
                        .setParameter("id", id.toString())
                        .setParameter("email", "u-" + id + "@test")
                        .setParameter("nick", nick(id))
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void suspended_with_deleted_at_blocked() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO users(id, email, nickname, status, deleted_at) " +
                                        "VALUES (CAST(:id AS uuid), :email, :nick, 'SUSPENDED', now())")
                        .setParameter("id", id.toString())
                        .setParameter("email", "u-" + id + "@test")
                        .setParameter("nick", nick(id))
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void valid_states_pass() {
        // ACTIVE / SUSPENDED / DELETED 정합성 충족 케이스
        UUID a = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        assertThatCode(() -> {
            User active = new User(a, "u-" + a + "@test", nick(a));
            userRepo.saveAndFlush(active);

            User suspended = new User(s, "u-" + s + "@test", nick(s));
            suspended.suspend();
            userRepo.saveAndFlush(suspended);

            User deleted = new User(d, "u-" + d + "@test", nick(d));
            deleted.softDelete();
            userRepo.saveAndFlush(deleted);
        }).doesNotThrowAnyException();
    }

    @Test
    void update_to_deleted_without_setting_deleted_at_blocked() {
        // 기존 ACTIVE 유저를 status='DELETED' 로만 직접 UPDATE → CHECK 위반
        UUID id = UUID.randomUUID();
        userRepo.saveAndFlush(new User(id, "u-" + id + "@test", nick(id)));

        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "UPDATE users SET status = 'DELETED' WHERE id = CAST(:id AS uuid)")
                        .setParameter("id", id.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    private static String nick(UUID id) {
        return "n-" + id.toString().substring(0, 8);
    }
}
