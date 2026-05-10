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
 * V2: idea_chat_messages.role ∈ {USER, ASSISTANT, SYSTEM} 화이트리스트.
 */
@Transactional
class IdeaChatMessageRoleCheckIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void unknown_role_blocked() {
        long sessionId = createSession();
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_chat_messages(session_id, role, content) " +
                                        "VALUES (:s, 'BOT', 'hi')")
                        .setParameter("s", sessionId)
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void all_valid_roles_pass() {
        long sessionId = createSession();
        assertThatCode(() -> {
            em.createNativeQuery(
                            "INSERT INTO idea_chat_messages(session_id, role, content) " +
                                    "VALUES (:s, 'USER', 'q')")
                    .setParameter("s", sessionId)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO idea_chat_messages(session_id, role, content) " +
                                    "VALUES (:s, 'ASSISTANT', 'a')")
                    .setParameter("s", sessionId)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO idea_chat_messages(session_id, role, content) " +
                                    "VALUES (:s, 'SYSTEM', 'sys')")
                    .setParameter("s", sessionId)
                    .executeUpdate();
        }).doesNotThrowAnyException();
    }

    private long createSession() {
        UUID user = UserFixture.create(userRepo);
        em.createNativeQuery(
                        "INSERT INTO idea_chat_sessions(user_id, status) " +
                                "VALUES (CAST(:u AS uuid), 'IN_PROGRESS')")
                .setParameter("u", user.toString())
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT currval('idea_chat_sessions_id_seq')")
                .getSingleResult()).longValue();
    }
}
