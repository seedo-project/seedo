package dev.seedo.credit;

import dev.seedo.credit.application.ChargeCommand;
import dev.seedo.credit.application.ChargeCreditService;
import dev.seedo.credit.application.ChargeResult;
import dev.seedo.credit.application.InsufficientCreditException;
import dev.seedo.credit.domain.CreditType;
import dev.seedo.credit.infrastructure.UserCreditRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ChargeCreditService} 의 정합성·동시성 보장.
 *
 * <p>이 IT 는 클래스 레벨 {@code @Transactional} 을 쓰지 않는다 — 동시성 테스트가 워커 스레드에서
 * 별도 트랜잭션을 열기 때문에 테스트 트랜잭션이 살아있으면 워커가 셋업 row 를 못 본다. 대신 매 테스트가
 * 새 UUID 로 사용자를 만들어 row-level 로 격리한다.
 */
class ChargeCreditServiceIT extends AbstractIntegrationTest {

    @Autowired
    private ChargeCreditService service;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private UserCreditRepository creditRepo;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    @Test
    void duplicate_call_with_same_reference_is_idempotent() {
        UUID uid = setupUserWithCredit(0L);
        String paymentId = "pay-" + UUID.randomUUID();
        ChargeCommand cmd = new ChargeCommand(uid, 100, CreditType.CHARGE, "PG_PAYMENT", paymentId, null);

        ChargeResult first = service.charge(cmd);
        ChargeResult second = service.charge(cmd);

        assertThat(first.idempotentSkip()).isFalse();
        assertThat(second.idempotentSkip()).isTrue();
        assertThat(first.balance()).isEqualTo(100);
        assertThat(second.balance()).isEqualTo(100);
        assertThat(second.transactionId()).isEqualTo(first.transactionId());

        assertThat(txCountFor(paymentId)).isEqualTo(1);
    }

    @Test
    void concurrent_charges_on_same_user_are_serialized() throws Exception {
        UUID uid = setupUserWithCredit(0L);
        int threads = 5;
        int amountEach = 100;

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ChargeResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(exec.submit(() -> {
                    start.await();
                    return service.charge(new ChargeCommand(
                            uid, amountEach, CreditType.ADJUST, null, null, "concurrent-" + idx));
                }));
            }
            start.countDown();
            for (Future<ChargeResult> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
        } finally {
            exec.shutdown();
        }

        long finalBalance = creditRepo.findById(uid).orElseThrow().getBalance();
        assertThat(finalBalance).isEqualTo((long) threads * amountEach);

        @SuppressWarnings("unchecked")
        List<Number> balances = em.createNativeQuery(
                        "SELECT balance_after FROM credit_transactions " +
                                "WHERE user_id = CAST(:uid AS uuid) ORDER BY balance_after")
                .setParameter("uid", uid.toString())
                .getResultList();
        assertThat(balances.stream().map(Number::longValue))
                .containsExactly(100L, 200L, 300L, 400L, 500L);
    }

    @Test
    void charge_pushing_balance_below_zero_is_rejected() {
        UUID uid = setupUserWithCredit(50L);

        assertThatThrownBy(() ->
                service.charge(new ChargeCommand(
                        uid, -100, CreditType.ADJUST, null, null, "underflow attempt"))
        ).isInstanceOf(InsufficientCreditException.class);

        long balance = creditRepo.findById(uid).orElseThrow().getBalance();
        assertThat(balance).isEqualTo(50L);
    }

    @Test
    void concurrent_calls_with_same_reference_yield_one_real_one_skip() throws Exception {
        UUID uid = setupUserWithCredit(0L);
        String paymentId = "pay-" + UUID.randomUUID();
        ChargeCommand cmd = new ChargeCommand(uid, 100, CreditType.CHARGE, "PG_PAYMENT", paymentId, null);

        ExecutorService exec = Executors.newFixedThreadPool(2);
        ChargeResult ra;
        ChargeResult rb;
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<ChargeResult> a = exec.submit(() -> {
                start.await();
                return service.charge(cmd);
            });
            Future<ChargeResult> b = exec.submit(() -> {
                start.await();
                return service.charge(cmd);
            });
            start.countDown();
            ra = a.get(15, TimeUnit.SECONDS);
            rb = b.get(15, TimeUnit.SECONDS);
        } finally {
            exec.shutdown();
        }

        int realCount = (ra.idempotentSkip() ? 0 : 1) + (rb.idempotentSkip() ? 0 : 1);
        assertThat(realCount).as("정확히 한 호출만 실제 적립").isEqualTo(1);

        long balance = creditRepo.findById(uid).orElseThrow().getBalance();
        assertThat(balance).isEqualTo(100L);

        assertThat(txCountFor(paymentId)).isEqualTo(1);
        assertThat(ra.transactionId()).isEqualTo(rb.transactionId());
    }

    private UUID setupUserWithCredit(long initialBalance) {
        return tx.execute(status -> {
            UUID id = UserFixture.create(userRepo);
            UserFixture.grantCredit(creditRepo, id, initialBalance);
            return id;
        });
    }

    private long txCountFor(String referenceId) {
        Number n = (Number) em.createNativeQuery(
                        "SELECT count(*) FROM credit_transactions WHERE reference_id = :rid")
                .setParameter("rid", referenceId)
                .getSingleResult();
        return n.longValue();
    }
}
