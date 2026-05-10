package dev.seedo.idea;

import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
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
 * V2: ideas 의 status='DELETED' ↔ deleted_at IS NOT NULL 양방향 정합성 (CLAUDE.md §6.4).
 * users 와 동일 패턴.
 */
@Transactional
class IdeaSoftDeleteInvariantIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void deleted_status_without_deleted_at_blocked() {
        UUID author = UserFixture.create(userRepo);
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO ideas(author_id, status) " +
                                        "VALUES (CAST(:a AS uuid), 'DELETED')")
                        .setParameter("a", author.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void draft_with_deleted_at_blocked() {
        UUID author = UserFixture.create(userRepo);
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO ideas(author_id, status, deleted_at) " +
                                        "VALUES (CAST(:a AS uuid), 'DRAFT', now())")
                        .setParameter("a", author.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void published_with_deleted_at_blocked() {
        UUID author = UserFixture.create(userRepo);
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO ideas(author_id, status, deleted_at) " +
                                        "VALUES (CAST(:a AS uuid), 'PUBLISHED', now())")
                        .setParameter("a", author.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void archived_with_deleted_at_blocked() {
        UUID author = UserFixture.create(userRepo);
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO ideas(author_id, status, deleted_at) " +
                                        "VALUES (CAST(:a AS uuid), 'ARCHIVED', now())")
                        .setParameter("a", author.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void valid_states_pass() {
        UUID author = UserFixture.create(userRepo);
        assertThatCode(() -> {
            em.createNativeQuery(
                            "INSERT INTO ideas(author_id, status) " +
                                    "VALUES (CAST(:a AS uuid), 'DRAFT')")
                    .setParameter("a", author.toString())
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO ideas(author_id, status) " +
                                    "VALUES (CAST(:a AS uuid), 'PUBLISHED')")
                    .setParameter("a", author.toString())
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO ideas(author_id, status) " +
                                    "VALUES (CAST(:a AS uuid), 'ARCHIVED')")
                    .setParameter("a", author.toString())
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO ideas(author_id, status, deleted_at) " +
                                    "VALUES (CAST(:a AS uuid), 'DELETED', now())")
                    .setParameter("a", author.toString())
                    .executeUpdate();
        }).doesNotThrowAnyException();
    }

}
