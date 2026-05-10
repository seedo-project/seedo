package dev.seedo.idea.application;

import dev.seedo.credit.application.ChargeCommand;
import dev.seedo.credit.application.ChargeCreditService;
import dev.seedo.credit.application.ChargeResult;
import dev.seedo.credit.domain.CreditType;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaPurchase;
import dev.seedo.idea.domain.IdeaStatus;
import dev.seedo.idea.infrastructure.IdeaPurchaseRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 아이디어 구매 코어 (CLAUDE.md §8.2). 5 단계를 한 트랜잭션에서 처리한다:
 * <ol>
 *   <li>{@code idea_purchases} 중복 체크 — 같은 (idea, buyer) 두 번째는 즉시 4xx</li>
 *   <li>{@code ideas} SELECT FOR UPDATE — 동시 구매 직렬화 + status·자가구매 검증</li>
 *   <li>{@link ChargeCreditService} 위임 — 잔액 락 + balance ≥ price 검증 + 원장 INSERT (SPEND, ref=IDEA_PURCHASE)</li>
 *   <li>{@code idea_purchases} INSERT — document_id 는 그 시점의 current_version_id 로 박제 (§6.5 산 시점 스냅샷)</li>
 * </ol>
 *
 * <p>마지막 방어선들: V2 의 {@code block_self_purchase} 트리거, {@code idea_purchases} UNIQUE(idea_id, buyer_id),
 * {@code credit_transactions} UNIQUE(reference_type, reference_id), {@code user_credits} balance &gt;= 0 CHECK.
 * 이 service 가 정상 호출 흐름을 4xx 로 변환하면, DB 가드는 우회·동시성 사고만 잡으면 된다.
 */
@Service
public class PurchaseIdeaService {

    private final IdeaRepository ideaRepo;
    private final IdeaPurchaseRepository purchaseRepo;
    private final ChargeCreditService chargeService;

    public PurchaseIdeaService(IdeaRepository ideaRepo,
                               IdeaPurchaseRepository purchaseRepo,
                               ChargeCreditService chargeService) {
        this.ideaRepo = ideaRepo;
        this.purchaseRepo = purchaseRepo;
        this.chargeService = chargeService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseResult purchase(Long ideaId, UUID buyerId) {
        if (purchaseRepo.existsByIdeaIdAndBuyerId(ideaId, buyerId)) {
            throw new AlreadyPurchasedException(ideaId, buyerId);
        }

        Idea idea = ideaRepo.findByIdForUpdate(ideaId)
                .orElseThrow(() -> new IdeaNotFoundException(ideaId));

        if (idea.getStatus() != IdeaStatus.PUBLISHED) {
            throw new IdeaNotPurchasableException(ideaId, idea.getStatus());
        }
        if (idea.getAuthorId().equals(buyerId)) {
            throw new SelfPurchaseException(ideaId, buyerId);
        }

        Long documentId = idea.getCurrentVersionId();
        if (documentId == null) {
            // PUBLISHED 인데 current_version_id 가 NULL — V2 finalize 트랜잭션에서 깨졌거나 직접 SQL 조작.
            // 정상 흐름에서는 발생 불가능, 데이터 정합성 사고이므로 5xx.
            throw new IllegalStateException(
                    "PUBLISHED idea has no current_version_id: ideaId=" + ideaId);
        }
        int price = idea.getPriceCredits();

        ChargeResult charge = chargeService.charge(new ChargeCommand(
                buyerId,
                -((long) price),
                CreditType.SPEND,
                "IDEA_PURCHASE",
                ideaId + ":" + buyerId,
                null
        ));

        IdeaPurchase purchase = purchaseRepo.saveAndFlush(
                new IdeaPurchase(ideaId, buyerId, documentId, charge.transactionId()));

        return new PurchaseResult(purchase.getId(), charge.balance(), documentId);
    }
}
