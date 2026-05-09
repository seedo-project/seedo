package dev.seedo.idea;

import dev.seedo.credit.domain.CreditTransaction;
import dev.seedo.credit.domain.CreditType;
import dev.seedo.credit.infrastructure.CreditTransactionRepository;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.domain.IdeaPurchase;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdeaPurchase JPA save 라운드트립 + existsByIdeaIdAndBuyerId 쿼리 검증 (§8.2 step 1).
 */
@Transactional
class IdeaPurchasePersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository docRepo;

    @Autowired
    private CreditTransactionRepository txRepo;

    @Autowired
    private IdeaPurchaseRepository purchaseRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void save_purchase_round_trip() {
        UUID author = createUser();
        UUID buyer = createUser();
        Idea idea = ideaRepo.saveAndFlush(new Idea(author, 10, 5));
        IdeaDocument doc = docRepo.saveAndFlush(new IdeaDocument(idea.getId(), 1, "t", "c"));
        CreditTransaction tx = txRepo.saveAndFlush(
                CreditTransaction.of(buyer, -10, CreditType.SPEND, 0, "IDEA_PURCHASE",
                        "test-" + UUID.randomUUID()));

        IdeaPurchase purchase = purchaseRepo.saveAndFlush(
                new IdeaPurchase(idea.getId(), buyer, doc.getId(), tx.getId()));

        em.clear();

        IdeaPurchase reloaded = purchaseRepo.findById(purchase.getId()).orElseThrow();
        assertThat(reloaded.getIdeaId()).isEqualTo(idea.getId());
        assertThat(reloaded.getBuyerId()).isEqualTo(buyer);
        assertThat(reloaded.getDocumentId()).isEqualTo(doc.getId());
        assertThat(reloaded.getTransactionId()).isEqualTo(tx.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void exists_by_idea_and_buyer_returns_correctly() {
        UUID author = createUser();
        UUID buyer = createUser();
        UUID otherBuyer = createUser();
        Idea idea = ideaRepo.saveAndFlush(new Idea(author, 10, 5));
        IdeaDocument doc = docRepo.saveAndFlush(new IdeaDocument(idea.getId(), 1, "t", "c"));
        CreditTransaction tx = txRepo.saveAndFlush(
                CreditTransaction.of(buyer, -10, CreditType.SPEND, 0, "IDEA_PURCHASE",
                        "test-" + UUID.randomUUID()));

        purchaseRepo.saveAndFlush(new IdeaPurchase(idea.getId(), buyer, doc.getId(), tx.getId()));

        assertThat(purchaseRepo.existsByIdeaIdAndBuyerId(idea.getId(), buyer)).isTrue();
        assertThat(purchaseRepo.existsByIdeaIdAndBuyerId(idea.getId(), otherBuyer)).isFalse();
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        userRepo.saveAndFlush(new User(id, "u-" + id + "@test", "n-" + id.toString().substring(0, 8)));
        return id;
    }
}
