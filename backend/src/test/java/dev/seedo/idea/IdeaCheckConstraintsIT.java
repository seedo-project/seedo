package dev.seedo.idea;

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
 * V2: ideas 의 단순 CHECK — status enum 화이트리스트, price/reward 부호.
 * 양방향 소프트삭제 정합성은 IdeaSoftDeleteInvariantIT 에서 분리해 다룬다.
 */
@Transactional
class IdeaCheckConstraintsIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void unknown_status_blocked() {
        UUID author = createUser();
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO ideas(author_id, status) " +
                                        "VALUES (CAST(:a AS uuid), 'BOGUS')")
                        .setParameter("a", author.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void negative_price_blocked() {
        UUID author = createUser();
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO ideas(author_id, price_credits) " +
                                        "VALUES (CAST(:a AS uuid), -1)")
                        .setParameter("a", author.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void negative_reward_blocked() {
        UUID author = createUser();
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO ideas(author_id, reward_credits) " +
                                        "VALUES (CAST(:a AS uuid), -1)")
                        .setParameter("a", author.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void zero_price_and_reward_pass() {
        // free idea / no reward 도 허용 — CHECK 는 >= 0
        UUID author = createUser();
        assertThatCode(() ->
                em.createNativeQuery(
                                "INSERT INTO ideas(author_id, price_credits, reward_credits) " +
                                        "VALUES (CAST(:a AS uuid), 0, 0)")
                        .setParameter("a", author.toString())
                        .executeUpdate()
        ).doesNotThrowAnyException();
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        userRepo.saveAndFlush(new User(id, "u-" + id + "@test", "n-" + id.toString().substring(0, 8)));
        return id;
    }
}
