package dev.seedo.credit.web;

import dev.seedo.credit.application.ChargeCommand;
import dev.seedo.credit.application.ChargeCreditService;
import dev.seedo.credit.application.ChargeResult;
import dev.seedo.credit.domain.CreditType;
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
public class AdminCreditController {

    private final ChargeCreditService chargeService;

    public AdminCreditController(ChargeCreditService chargeService) {
        this.chargeService = chargeService;
    }

    @PostMapping("/grant")
    @PreAuthorize("hasAuthority('PERM_CREDIT_ADJUST')")
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
