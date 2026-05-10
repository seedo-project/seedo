package dev.seedo.credit;

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
import static dev.seedo.support.AbstractIntegrationTest.assertSqlState;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V1 §6.1: credit_transactions 는 append-only. UPDATE/DELETE 는 block_credit_tx_modification()
 * 트리거가 차단한다. 정정은 type=ADJUST 새 row 로 처리.
 */
@Transactional
class CreditTransactionAppendOnlyIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CreditTransactionRepository txRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void update_via_native_query_blocked_by_trigger() {
        UUID uid = UserFixture.create(userRepo);
        CreditTransaction tx = txRepo.saveAndFlush(
                CreditTransaction.of(uid, 100, CreditType.CHARGE, 100, "PG_PAYMENT", "p-" + UUID.randomUUID())
        );

        assertThatThrownBy(() ->
                em.createNativeQuery("UPDATE credit_transactions SET description = 'tampered' WHERE id = :id")
                        .setParameter("id", tx.getId())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_RAISE_EXCEPTION));
    }

    @Test
    void delete_blocked_by_trigger() {
        UUID uid = UserFixture.create(userRepo);
        CreditTransaction tx = txRepo.saveAndFlush(
                CreditTransaction.of(uid, 100, CreditType.CHARGE, 100, "PG_PAYMENT", "p-" + UUID.randomUUID())
        );

        assertThatThrownBy(() -> {
            txRepo.delete(tx);
            txRepo.flush();
        }).satisfies(t -> assertSqlState(t, SQLSTATE_RAISE_EXCEPTION));
    }

}
