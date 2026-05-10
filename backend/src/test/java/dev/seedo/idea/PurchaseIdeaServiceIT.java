package dev.seedo.idea;

import dev.seedo.credit.domain.UserCredit;
import dev.seedo.credit.infrastructure.UserCreditRepository;
import dev.seedo.idea.application.AlreadyPurchasedException;
import dev.seedo.idea.application.IdeaNotPurchasableException;
import dev.seedo.idea.application.PurchaseIdeaService;
import dev.seedo.idea.application.PurchaseResult;
import dev.seedo.idea.application.SelfPurchaseException;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaPurchaseRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.user.domain.User;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

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
 * {@link PurchaseIdeaService} 의 유스케이스·정합성·동시성 보장.
 *
 * <p>클래스 레벨 {@code @Transactional} 미사용 — 동시성 테스트에서 워커 스레드가 셋업 row 를 봐야 함.
 * 매 테스트는 새 UUID 사용자/아이디어로 격리된다.
 */
class PurchaseIdeaServiceIT extends AbstractIntegrationTest {

    @Autowired
    private PurchaseIdeaService service;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private UserCreditRepository creditRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository docRepo;

    @Autowired
    private IdeaPurchaseRepository purchaseRepo;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    @Test
    void happy_path_charges_credits_and_records_purchase() {
        Fixture f = setupPublishedIdea(10, 50L);

        PurchaseResult result = service.purchase(f.ideaId, f.buyer);

        assertThat(result.balance()).isEqualTo(40L);
        assertThat(result.documentId()).isEqualTo(f.documentId);
        assertThat(result.purchaseId()).isPositive();

        assertThat(creditRepo.findById(f.buyer).orElseThrow().getBalance()).isEqualTo(40L);
        assertThat(purchaseRepo.existsByIdeaIdAndBuyerId(f.ideaId, f.buyer)).isTrue();
    }

    @Test
    void duplicate_purchase_blocked_at_service() {
        Fixture f = setupPublishedIdea(10, 50L);
        service.purchase(f.ideaId, f.buyer);

        assertThatThrownBy(() -> service.purchase(f.ideaId, f.buyer))
                .isInstanceOf(AlreadyPurchasedException.class);

        assertThat(creditRepo.findById(f.buyer).orElseThrow().getBalance()).isEqualTo(40L);
    }

    @Test
    void self_purchase_blocked() {
        Fixture f = setupPublishedIdea(10, 50L);
        creditUser(f.author, 50L);

        assertThatThrownBy(() -> service.purchase(f.ideaId, f.author))
                .isInstanceOf(SelfPurchaseException.class);

        assertThat(creditRepo.findById(f.author).orElseThrow().getBalance()).isEqualTo(50L);
    }

    @Test
    void draft_idea_not_purchasable() {
        Fixture f = setupDraftIdea(10, 50L);

        assertThatThrownBy(() -> service.purchase(f.ideaId, f.buyer))
                .isInstanceOf(IdeaNotPurchasableException.class);

        assertThat(creditRepo.findById(f.buyer).orElseThrow().getBalance()).isEqualTo(50L);
    }

    @Test
    void insufficient_balance_rejected() {
        Fixture f = setupPublishedIdea(100, 50L);

        assertThatThrownBy(() -> service.purchase(f.ideaId, f.buyer))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(creditRepo.findById(f.buyer).orElseThrow().getBalance()).isEqualTo(50L);
        assertThat(purchaseRepo.existsByIdeaIdAndBuyerId(f.ideaId, f.buyer)).isFalse();
    }

    @Test
    void concurrent_purchases_by_two_buyers_both_succeed_serialized() throws Exception {
        UUID author = createUser();
        UUID b1 = createUser();
        UUID b2 = createUser();
        creditUser(b1, 50L);
        creditUser(b2, 50L);
        Long ideaId = tx.execute(s -> {
            Idea idea = ideaRepo.saveAndFlush(new Idea(author, 10, 5));
            IdeaDocument doc = docRepo.saveAndFlush(new IdeaDocument(idea.getId(), 1, "t", "c"));
            idea.updateCurrentVersion(doc.getId());
            idea.publish();
            ideaRepo.saveAndFlush(idea);
            return idea.getId();
        });

        ExecutorService exec = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<PurchaseResult> fa = exec.submit(() -> {
                start.await();
                return service.purchase(ideaId, b1);
            });
            Future<PurchaseResult> fb = exec.submit(() -> {
                start.await();
                return service.purchase(ideaId, b2);
            });
            start.countDown();
            fa.get(15, TimeUnit.SECONDS);
            fb.get(15, TimeUnit.SECONDS);
        } finally {
            exec.shutdown();
        }

        assertThat(creditRepo.findById(b1).orElseThrow().getBalance()).isEqualTo(40L);
        assertThat(creditRepo.findById(b2).orElseThrow().getBalance()).isEqualTo(40L);
        assertThat(purchaseRepo.existsByIdeaIdAndBuyerId(ideaId, b1)).isTrue();
        assertThat(purchaseRepo.existsByIdeaIdAndBuyerId(ideaId, b2)).isTrue();
    }

    @Test
    void concurrent_purchases_by_same_buyer_only_one_succeeds() throws Exception {
        Fixture f = setupPublishedIdea(10, 50L);

        ExecutorService exec = Executors.newFixedThreadPool(2);
        List<Throwable> errors;
        int successCount;
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<PurchaseResult> a = exec.submit(() -> {
                start.await();
                return service.purchase(f.ideaId, f.buyer);
            });
            Future<PurchaseResult> b = exec.submit(() -> {
                start.await();
                return service.purchase(f.ideaId, f.buyer);
            });
            start.countDown();
            errors = new java.util.ArrayList<>();
            int success = 0;
            for (Future<PurchaseResult> fut : List.of(a, b)) {
                try {
                    fut.get(15, TimeUnit.SECONDS);
                    success++;
                } catch (Exception e) {
                    errors.add(e.getCause() != null ? e.getCause() : e);
                }
            }
            successCount = success;
        } finally {
            exec.shutdown();
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(errors).hasSize(1);
        assertThat(creditRepo.findById(f.buyer).orElseThrow().getBalance()).isEqualTo(40L);

        @SuppressWarnings("unchecked")
        List<Number> count = em.createNativeQuery(
                        "SELECT count(*) FROM idea_purchases WHERE idea_id = :id AND buyer_id = CAST(:buyer AS uuid)")
                .setParameter("id", f.ideaId)
                .setParameter("buyer", f.buyer.toString())
                .getResultList();
        assertThat(count.get(0).longValue()).isEqualTo(1L);
    }

    private record Fixture(UUID author, UUID buyer, Long ideaId, Long documentId) {
    }

    private Fixture setupPublishedIdea(int price, long buyerInitialBalance) {
        UUID author = createUser();
        UUID buyer = createUser();
        creditUser(buyer, buyerInitialBalance);

        return tx.execute(status -> {
            Idea idea = ideaRepo.saveAndFlush(new Idea(author, price, 5));
            IdeaDocument doc = docRepo.saveAndFlush(new IdeaDocument(idea.getId(), 1, "t", "c"));
            idea.updateCurrentVersion(doc.getId());
            idea.publish();
            ideaRepo.saveAndFlush(idea);
            return new Fixture(author, buyer, idea.getId(), doc.getId());
        });
    }

    private Fixture setupDraftIdea(int price, long buyerInitialBalance) {
        UUID author = createUser();
        UUID buyer = createUser();
        creditUser(buyer, buyerInitialBalance);

        return tx.execute(status -> {
            Idea idea = ideaRepo.saveAndFlush(new Idea(author, price, 5));
            IdeaDocument doc = docRepo.saveAndFlush(new IdeaDocument(idea.getId(), 1, "t", "c"));
            return new Fixture(author, buyer, idea.getId(), doc.getId());
        });
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        tx.execute(s -> userRepo.saveAndFlush(
                new User(id, "u-" + id + "@test", "n-" + id.toString().substring(0, 8))));
        return id;
    }

    private void creditUser(UUID userId, long balance) {
        tx.execute(s -> {
            UserCredit uc = new UserCredit(userId);
            if (balance > 0) {
                uc.applyDelta(balance);
            }
            return creditRepo.saveAndFlush(uc);
        });
    }
}
