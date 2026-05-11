package dev.seedo.credit.web;

import dev.seedo.credit.application.ChargeCommand;
import dev.seedo.credit.application.ChargeCreditService;
import dev.seedo.credit.application.ChargeResult;
import dev.seedo.credit.domain.CreditType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 크레딧 적립 엔드포인트. MVP 단계에서 결제 연동 (PortOne) 도입 전 무료 크레딧 발급 경로.
 *
 * <p>{@code PERM_CREDIT_ADJUST} 권한 보유자만 호출 가능 (V1 시드: ADMIN role 만). type 은 ADJUST —
 * 결제·보상이 아닌 운영자 수동 정정. 멱등성 키는 사용하지 않음 (운영자 더블클릭 방지는 클라이언트 책임,
 * 같은 trade-off 가 더블클릭 자체보다 보수적).
 */
@RestController
@RequestMapping("/admin/credit")
@Tag(name = "관리자 크레딧", description = "관리자가 사용자에게 크레딧을 적립한다. MVP 단계 무료 발급 경로 (PortOne 도입 전).")
public class AdminCreditController {

    private final ChargeCreditService chargeService;

    public AdminCreditController(ChargeCreditService chargeService) {
        this.chargeService = chargeService;
    }

    @PostMapping("/grant")
    @PreAuthorize("hasAuthority('PERM_CREDIT_ADJUST')")
    @Operation(
            summary = "크레딧 적립 (관리자)",
            description = """
                    지정한 사용자에게 크레딧을 적립한다. type 은 ADJUST (운영자 수동 정정).

                    - 잔액 갱신과 원장(credit_transactions) INSERT 가 같은 트랜잭션에서 처리된다.
                    - 멱등성 키는 사용하지 않으므로 동일 요청을 두 번 보내면 두 번 적립된다 — 클라이언트가
                      더블클릭을 방지해야 한다.
                    - 결제 webhook 이 도입되면 별도 엔드포인트(CHARGE type)로 분리된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "적립 성공. 적립 후 잔액과 원장 ID 를 반환."),
            @ApiResponse(responseCode = "400", description = "요청 검증 실패 (userId 누락 / amount 가 양수가 아님 / reason 누락)", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT 누락 또는 만료", content = @Content),
            @ApiResponse(responseCode = "403", description = "PERM_CREDIT_ADJUST 권한 없음 (ADMIN role 필요)", content = @Content)
    })
    public AdminGrantResponse grant(@Valid @RequestBody AdminGrantRequest req) {
        ChargeResult result = chargeService.charge(new ChargeCommand(
                req.userId(),
                req.amount(),
                CreditType.ADJUST,
                null,
                null,
                req.reason()
        ));
        return new AdminGrantResponse(result.balance(), result.transactionId());
    }
}
