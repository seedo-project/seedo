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
import static dev.seedo.support.AbstractIntegrationTest.SQLSTATE_UNIQUE_VIOLATION;
import static dev.seedo.support.AbstractIntegrationTest.assertSqlState;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V2 §6.5: idea_documents 는 (idea_id, version) UNIQUE — published 후 수정도 새 row.
 * version >= 1 CHECK 는 v0 같은 무의미 row 차단.
 */
@Transactional
class IdeaDocumentVersionUniqueIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void duplicate_version_blocked() {
        long ideaId = createDraftIdea();

        em.createNativeQuery(
                        "INSERT INTO idea_documents(idea_id, version, title, content_md) " +
                                "VALUES (:idea, 1, 't', 'c')")
                .setParameter("idea", ideaId)
                .executeUpdate();

        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_documents(idea_id, version, title, content_md) " +
                                        "VALUES (:idea, 1, 't2', 'c2')")
                        .setParameter("idea", ideaId)
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_UNIQUE_VIOLATION));
    }

    @Test
    void different_versions_pass() {
        long ideaId = createDraftIdea();
        assertThatCode(() -> {
            em.createNativeQuery(
                            "INSERT INTO idea_documents(idea_id, version, title, content_md) " +
                                    "VALUES (:idea, 1, 't1', 'c1')")
                    .setParameter("idea", ideaId)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO idea_documents(idea_id, version, title, content_md) " +
                                    "VALUES (:idea, 2, 't2', 'c2')")
                    .setParameter("idea", ideaId)
                    .executeUpdate();
        }).doesNotThrowAnyException();
    }

    @Test
    void same_version_across_ideas_pass() {
        // (idea_id, version) 복합 UNIQUE 라 다른 아이디어의 v1 은 충돌 안 함.
        long a = createDraftIdea();
        long b = createDraftIdea();
        assertThatCode(() -> {
            em.createNativeQuery(
                            "INSERT INTO idea_documents(idea_id, version, title, content_md) " +
                                    "VALUES (:idea, 1, 't', 'c')")
                    .setParameter("idea", a)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO idea_documents(idea_id, version, title, content_md) " +
                                    "VALUES (:idea, 1, 't', 'c')")
                    .setParameter("idea", b)
                    .executeUpdate();
        }).doesNotThrowAnyException();
    }

    @Test
    void zero_version_blocked() {
        long ideaId = createDraftIdea();
        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_documents(idea_id, version, title, content_md) " +
                                        "VALUES (:idea, 0, 't', 'c')")
                        .setParameter("idea", ideaId)
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    private long createDraftIdea() {
        UUID author = UserFixture.create(userRepo);
        em.createNativeQuery(
                        "INSERT INTO ideas(author_id) VALUES (CAST(:a AS uuid))")
                .setParameter("a", author.toString())
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT currval('ideas_id_seq')")
                .getSingleResult()).longValue();
    }
}
