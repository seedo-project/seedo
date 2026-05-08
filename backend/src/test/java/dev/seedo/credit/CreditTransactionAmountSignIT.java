package dev.seedo.credit;

import dev.seedo.credit.entity.CreditTransaction;
import dev.seedo.credit.entity.CreditType;
import dev.seedo.credit.repository.CreditTransactionRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.user.entity.User;
import dev.seedo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static dev.seedo.support.AbstractIntegrationTest.SQLSTATE_CHECK_VIOLATION;
import static dev.seedo.support.AbstractIntegrationTest.assertSqlState;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V1: type 별 amount 부호 정합성.
 *   CHARGE / REWARD / REFUND > 0  (잔액 증가)
 *   SPEND                   < 0  (잔액 감소)
 *   ADJUST                  != 0 (정정)
 * 잘못된 부호로 INSERT 시 잔액 캐시·원장 합계가 어긋나는 사고를 1차 차단.
 */
@Transactional
class CreditTransactionAmountSignIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CreditTransactionRepository txRepo;

    @Test
    void charge_with_negative_amount_blocked() {
        UUID uid = createUser();
        assertThatThrownBy(() ->
                txRepo.saveAndFlush(
                        CreditTransaction.of(uid, -100, CreditType.CHARGE, 0, "PG_PAYMENT", "p-" + UUID.randomUUID())
                )
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void spend_with_positive_amount_blocked() {
        UUID uid = createUser();
        assertThatThrownBy(() ->
                txRepo.saveAndFlush(
                        CreditTransaction.of(uid, 50, CreditType.SPEND, 100, "IDEA_PURCHASE", "i-" + UUID.randomUUID())
                )
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void refund_with_negative_amount_blocked() {
        // REFUND 컨벤션: 수령자 잔액 증가 = 양수
        UUID uid = createUser();
        assertThatThrownBy(() ->
                txRepo.saveAndFlush(
                        CreditTransaction.of(uid, -20, CreditType.REFUND, 0, "PG_PAYMENT", "r-" + UUID.randomUUID())
                )
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void adjust_with_zero_amount_blocked() {
        UUID uid = createUser();
        assertThatThrownBy(() ->
                txRepo.saveAndFlush(CreditTransaction.adjust(uid, 0, 0, "no-op"))
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void valid_signs_pass() {
        UUID uid = createUser();
        assertThatCode(() -> {
            txRepo.saveAndFlush(CreditTransaction.of(uid, 100, CreditType.CHARGE, 100, "PG_PAYMENT", "p-" + UUID.randomUUID()));
            txRepo.saveAndFlush(CreditTransaction.of(uid, -10, CreditType.SPEND, 90, "IDEA_PURCHASE", "i-" + UUID.randomUUID()));
            txRepo.saveAndFlush(CreditTransaction.of(uid, 5, CreditType.REWARD, 95, "ADOPTION", "a-" + UUID.randomUUID()));
            txRepo.saveAndFlush(CreditTransaction.of(uid, 20, CreditType.REFUND, 115, "PG_PAYMENT", "r-" + UUID.randomUUID()));
            txRepo.saveAndFlush(CreditTransaction.adjust(uid, -50, 65, "admin adjust"));
        }).doesNotThrowAnyException();
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        userRepo.saveAndFlush(new User(id, "u-" + id + "@test", "n-" + id.toString().substring(0, 8)));
        return id;
    }
}
