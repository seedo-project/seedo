package dev.seedo.credit;

import dev.seedo.credit.domain.CreditTransaction;
import dev.seedo.credit.domain.CreditType;
import dev.seedo.credit.infrastructure.CreditTransactionRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
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
 * V1 §6.7: PG webhook 등 외부 호출 멱등성. 같은 (reference_type, reference_id) 두 번 INSERT 차단.
 * 부분 NULL (한쪽만 채워진 row) 도 CHECK 로 차단.
 */
@Transactional
class CreditTransactionIdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CreditTransactionRepository txRepo;

    @Test
    void duplicate_reference_pair_blocked_by_unique_index() {
        UUID uid = UserFixture.create(userRepo);
        String paymentId = "pay-" + UUID.randomUUID();
        txRepo.saveAndFlush(
                CreditTransaction.of(uid, 100, CreditType.CHARGE, 100, "PG_PAYMENT", paymentId)
        );

        assertThatThrownBy(() ->
                txRepo.saveAndFlush(
                        CreditTransaction.of(uid, 100, CreditType.CHARGE, 200, "PG_PAYMENT", paymentId)
                )
        ).satisfies(t -> assertSqlState(t, SQLSTATE_UNIQUE_VIOLATION));
    }

    @Test
    void multiple_null_reference_rows_allowed() {
        // ADJUST 처럼 reference 가 둘 다 NULL 인 row 는 partial UNIQUE 영향 받지 않음
        UUID uid = UserFixture.create(userRepo);
        assertThatCode(() -> {
            txRepo.saveAndFlush(CreditTransaction.adjust(uid, 50, 50, "manual fix 1"));
            txRepo.saveAndFlush(CreditTransaction.adjust(uid, -10, 40, "manual fix 2"));
        }).doesNotThrowAnyException();
    }

    @Test
    void partial_null_reference_blocked_by_check() {
        // reference_type 만 채우고 reference_id 는 NULL → CHECK ((type IS NULL) = (id IS NULL)) 위반
        UUID uid = UserFixture.create(userRepo);
        assertThatThrownBy(() ->
                txRepo.saveAndFlush(
                        CreditTransaction.of(uid, 100, CreditType.CHARGE, 100, "PG_PAYMENT", null)
                )
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

}
