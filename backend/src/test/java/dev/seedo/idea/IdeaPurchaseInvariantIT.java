package dev.seedo.idea;

import dev.seedo.credit.domain.CreditTransaction;
import dev.seedo.credit.domain.CreditType;
import dev.seedo.credit.infrastructure.CreditTransactionRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static dev.seedo.support.AbstractIntegrationTest.SQLSTATE_RAISE_EXCEPTION;
import static dev.seedo.support.AbstractIntegrationTest.SQLSTATE_UNIQUE_VIOLATION;
import static dev.seedo.support.AbstractIntegrationTest.assertSqlState;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V2 §6.6: 본인 아이디어 구매 차단 (block_self_purchase 트리거)
 *      §5.3: idea_purchases UNIQUE(idea_id, buyer_id), UNIQUE(transaction_id)
 */
@Transactional
class IdeaPurchaseInvariantIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CreditTransactionRepository txRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void self_purchase_blocked_by_trigger() {
        UUID author = UserFixture.create(userRepo);
        long ideaId = createIdea(author);
        long docId = createDocument(ideaId, 1);
        long txId = spendTransaction(author);

        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_purchases(idea_id, buyer_id, document_id, transaction_id) " +
                                        "VALUES (:i, CAST(:b AS uuid), :d, :t)")
                        .setParameter("i", ideaId)
                        .setParameter("b", author.toString())
                        .setParameter("d", docId)
                        .setParameter("t", txId)
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_RAISE_EXCEPTION));
    }

    @Test
    void duplicate_idea_buyer_blocked() {
        UUID author = UserFixture.create(userRepo);
        UUID buyer = UserFixture.create(userRepo);
        long ideaId = createIdea(author);
        long docId = createDocument(ideaId, 1);
        long tx1 = spendTransaction(buyer);
        long tx2 = spendTransaction(buyer);

        em.createNativeQuery(
                        "INSERT INTO idea_purchases(idea_id, buyer_id, document_id, transaction_id) " +
                                "VALUES (:i, CAST(:b AS uuid), :d, :t)")
                .setParameter("i", ideaId)
                .setParameter("b", buyer.toString())
                .setParameter("d", docId)
                .setParameter("t", tx1)
                .executeUpdate();

        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_purchases(idea_id, buyer_id, document_id, transaction_id) " +
                                        "VALUES (:i, CAST(:b AS uuid), :d, :t)")
                        .setParameter("i", ideaId)
                        .setParameter("b", buyer.toString())
                        .setParameter("d", docId)
                        .setParameter("t", tx2)
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_UNIQUE_VIOLATION));
    }

    @Test
    void duplicate_transaction_blocked() {
        // 동일 credit_transactions 행이 2개 구매를 점하는 것은 부정합 — UNIQUE(transaction_id) 가 차단.
        UUID author = UserFixture.create(userRepo);
        UUID buyer = UserFixture.create(userRepo);
        long ideaA = createIdea(author);
        long ideaB = createIdea(author);
        long docA = createDocument(ideaA, 1);
        long docB = createDocument(ideaB, 1);
        long tx = spendTransaction(buyer);

        em.createNativeQuery(
                        "INSERT INTO idea_purchases(idea_id, buyer_id, document_id, transaction_id) " +
                                "VALUES (:i, CAST(:b AS uuid), :d, :t)")
                .setParameter("i", ideaA)
                .setParameter("b", buyer.toString())
                .setParameter("d", docA)
                .setParameter("t", tx)
                .executeUpdate();

        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_purchases(idea_id, buyer_id, document_id, transaction_id) " +
                                        "VALUES (:i, CAST(:b AS uuid), :d, :t)")
                        .setParameter("i", ideaB)
                        .setParameter("b", buyer.toString())
                        .setParameter("d", docB)
                        .setParameter("t", tx)
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_UNIQUE_VIOLATION));
    }

    @Test
    void distinct_buyer_distinct_tx_passes() {
        UUID author = UserFixture.create(userRepo);
        UUID buyer = UserFixture.create(userRepo);
        long ideaId = createIdea(author);
        long docId = createDocument(ideaId, 1);
        long txId = spendTransaction(buyer);

        assertThatCode(() ->
                em.createNativeQuery(
                                "INSERT INTO idea_purchases(idea_id, buyer_id, document_id, transaction_id) " +
                                        "VALUES (:i, CAST(:b AS uuid), :d, :t)")
                        .setParameter("i", ideaId)
                        .setParameter("b", buyer.toString())
                        .setParameter("d", docId)
                        .setParameter("t", txId)
                        .executeUpdate()
        ).doesNotThrowAnyException();
    }

    private long createIdea(UUID author) {
        em.createNativeQuery(
                        "INSERT INTO ideas(author_id, status) " +
                                "VALUES (CAST(:a AS uuid), 'PUBLISHED')")
                .setParameter("a", author.toString())
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT currval('ideas_id_seq')")
                .getSingleResult()).longValue();
    }

    private long createDocument(long ideaId, int version) {
        em.createNativeQuery(
                        "INSERT INTO idea_documents(idea_id, version, title, content_md) " +
                                "VALUES (:i, :v, 't', 'c')")
                .setParameter("i", ideaId)
                .setParameter("v", version)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT currval('idea_documents_id_seq')")
                .getSingleResult()).longValue();
    }

    private long spendTransaction(UUID buyer) {
        // balance_after=0 으로 두는 건 합법 — CHECK 는 >= 0. 이 IT 는 잔액 캐시를 다루지 않으므로 OK.
        CreditTransaction tx = txRepo.saveAndFlush(
                CreditTransaction.of(buyer, -10, CreditType.SPEND, 0, "IDEA_PURCHASE",
                        "test-" + UUID.randomUUID()));
        return tx.getId();
    }
}
