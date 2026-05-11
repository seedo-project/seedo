package dev.seedo.idea.web;

import dev.seedo.common.web.CurrentUserId;
import dev.seedo.idea.application.PurchaseIdeaService;
import dev.seedo.idea.application.PurchaseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "아이디어 구매", description = "공개된 아이디어 본문을 크레딧으로 구매한다. 트랜잭션 내에서 잔액 차감 + 원장 INSERT + 구매 기록 INSERT 가 같이 처리된다.")
public class IdeaPurchaseController {

    private final PurchaseIdeaService service;

    public IdeaPurchaseController(PurchaseIdeaService service) {
        this.service = service;
    }

    @PostMapping("/{id}/purchase")
    @PreAuthorize("hasAuthority('PERM_IDEA_PURCHASE')")
    @Operation(
            summary = "아이디어 구매",
            description = """
                    공개 상태(PUBLISHED) 의 아이디어 본문 열람권을 구매한다.

                    - 본인 아이디어는 구매할 수 없다 (자가 구매 차단).
                    - 동일 아이디어를 두 번 구매할 수 없다 (UNIQUE(idea_id, buyer_id)).
                    - 잔액이 부족하면 거래가 롤백된다.
                    - 구매 시점의 `current_version_id` 가 `idea_purchases.document_id` 에 스냅샷으로 저장되어,
                      이후 작성자가 새 버전을 발행해도 구매자는 산 시점의 본문 + 최신 본문 모두 열람 가능하다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구매 성공. 신규 잔액과 스냅샷 문서 ID 를 반환."),
            @ApiResponse(responseCode = "400", description = "잔액 부족 / 자가 구매 / 공개 상태가 아닌 아이디어", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "401", description = "JWT 누락 또는 만료", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "PERM_IDEA_PURCHASE 권한 없음", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "404", description = "해당 아이디어가 존재하지 않음", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "409", description = "이미 구매한 아이디어 (UNIQUE 위반 race 포함)", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public PurchaseResponse purchase(
            @Parameter(description = "구매할 아이디어의 ID", example = "42", required = true)
            @PathVariable("id") Long id,
            @CurrentUserId UUID buyerId) {
        PurchaseResult result = service.purchase(id, buyerId);
        return new PurchaseResponse(result.purchaseId(), result.balance(), result.documentId());
    }
}
