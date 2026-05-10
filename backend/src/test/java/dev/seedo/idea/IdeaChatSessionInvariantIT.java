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
 * V2: idea_chat_sessions 의 status enum + 양방향 정합성.
 * - status='FINALIZED' ↔ idea_id NOT NULL
 * - status='FINALIZED' ↔ finalized_at NOT NULL
 * - status='ABANDONED' ↔ abandoned_at NOT NULL
 */
@Transactional
class IdeaChatSessionInvariantIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void unknown_status_blocked() {
        UUID user = UserFixture.create(userRepo);
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_chat_sessions(user_id, status) " +
                                        "VALUES (CAST(:u AS uuid), 'BOGUS')")
                        .setParameter("u", user.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void in_progress_with_idea_id_blocked() {
        UUID user = UserFixture.create(userRepo);
        long ideaId = createIdea(user);
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_chat_sessions(user_id, idea_id, status) " +
                                        "VALUES (CAST(:u AS uuid), :i, 'IN_PROGRESS')")
                        .setParameter("u", user.toString())
                        .setParameter("i", ideaId)
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void finalized_without_idea_id_blocked() {
        UUID user = UserFixture.create(userRepo);
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_chat_sessions(user_id, status, finalized_at) " +
                                        "VALUES (CAST(:u AS uuid), 'FINALIZED', now())")
                        .setParameter("u", user.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void finalized_without_finalized_at_blocked() {
        UUID user = UserFixture.create(userRepo);
        long ideaId = createIdea(user);
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_chat_sessions(user_id, idea_id, status) " +
                                        "VALUES (CAST(:u AS uuid), :i, 'FINALIZED')")
                        .setParameter("u", user.toString())
                        .setParameter("i", ideaId)
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void in_progress_with_finalized_at_blocked() {
        UUID user = UserFixture.create(userRepo);
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_chat_sessions(user_id, status, finalized_at) " +
                                        "VALUES (CAST(:u AS uuid), 'IN_PROGRESS', now())")
                        .setParameter("u", user.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void abandoned_without_abandoned_at_blocked() {
        UUID user = UserFixture.create(userRepo);
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_chat_sessions(user_id, status) " +
                                        "VALUES (CAST(:u AS uuid), 'ABANDONED')")
                        .setParameter("u", user.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void in_progress_with_abandoned_at_blocked() {
        UUID user = UserFixture.create(userRepo);
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_chat_sessions(user_id, status, abandoned_at) " +
                                        "VALUES (CAST(:u AS uuid), 'IN_PROGRESS', now())")
                        .setParameter("u", user.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void valid_lifecycles_pass() {
        UUID user = UserFixture.create(userRepo);
        long ideaId = createIdea(user);
        assertThatCode(() -> {
            em.createNativeQuery(
                            "INSERT INTO idea_chat_sessions(user_id, status) " +
                                    "VALUES (CAST(:u AS uuid), 'IN_PROGRESS')")
                    .setParameter("u", user.toString())
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO idea_chat_sessions(user_id, idea_id, status, finalized_at) " +
                                    "VALUES (CAST(:u AS uuid), :i, 'FINALIZED', now())")
                    .setParameter("u", user.toString())
                    .setParameter("i", ideaId)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO idea_chat_sessions(user_id, status, abandoned_at) " +
                                    "VALUES (CAST(:u AS uuid), 'ABANDONED', now())")
                    .setParameter("u", user.toString())
                    .executeUpdate();
        }).doesNotThrowAnyException();
    }

    private long createIdea(UUID author) {
        em.createNativeQuery("INSERT INTO ideas(author_id) VALUES (CAST(:a AS uuid))")
                .setParameter("a", author.toString())
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT currval('ideas_id_seq')")
                .getSingleResult()).longValue();
    }
}
