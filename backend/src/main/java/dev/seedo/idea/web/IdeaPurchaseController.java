package dev.seedo.idea.web;

import dev.seedo.common.web.CurrentUserId;
import dev.seedo.idea.application.PurchaseIdeaService;
import dev.seedo.idea.application.PurchaseResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 아이디어 구매. buyer 는 JWT 의 {@code sub} claim 으로 식별 — {@link CurrentUserId} 가 자동 주입한다.
 * 클라이언트가 path/body 로 buyer 를 명시할 수 없어 대리 구매가 차단된다.
 *
 * <p>실제 매핑 경로는 {@code /api/v1/ideas/{id}/purchase} — {@code WebMvcConfig} 가 모든 RestController 에
 * {@code /api/v1} prefix 를 자동 부착한다.
 */
@RestController
@RequestMapping("/ideas")
public class IdeaPurchaseController {

    private final PurchaseIdeaService service;

    public IdeaPurchaseController(PurchaseIdeaService service) {
        this.service = service;
    }

    @PostMapping("/{id}/purchase")
    @PreAuthorize("hasAuthority('PERM_IDEA_PURCHASE')")
    public PurchaseResponse purchase(@PathVariable("id") Long id, @CurrentUserId UUID buyerId) {
        PurchaseResult result = service.purchase(id, buyerId);
        return new PurchaseResponse(result.purchaseId(), result.balance(), result.documentId());
    }
}
