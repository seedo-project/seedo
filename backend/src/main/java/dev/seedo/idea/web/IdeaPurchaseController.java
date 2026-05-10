package dev.seedo.idea.web;

import dev.seedo.idea.application.PurchaseIdeaService;
import dev.seedo.idea.application.PurchaseResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 아이디어 구매. buyer 는 JWT 의 {@code sub} claim 으로 식별 — 클라이언트가 직접 명시 불가 (대리 구매 차단).
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
    public PurchaseResponse purchase(@PathVariable("id") Long id, JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        UUID buyerId = UUID.fromString(jwt.getSubject());
        PurchaseResult result = service.purchase(id, buyerId);
        return new PurchaseResponse(result.purchaseId(), result.balance(), result.documentId());
    }
}
