package dev.seedo.credit.web;

import dev.seedo.credit.application.ChargeCommand;
import dev.seedo.credit.application.ChargeCreditService;
import dev.seedo.credit.application.ChargeResult;
import dev.seedo.credit.domain.CreditType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * PG (PortOne) 결제 webhook 수신. 같은 결제 알림이 재시도로 두세 번 도착해도 멱등성 키
 * ({@code reference_type=PG_PAYMENT}, {@code reference_id=paymentId}) 로 한 번만 충전된다 (CLAUDE.md §8.1).
 *
 * <p>인증: Spring Security 의 JWT 보호를 우회한다 (PG 가 호출, JWT 없음). 대신 {@code X-Webhook-Secret}
 * 헤더와 {@code payment.webhook.secret} 설정값을 timing-safe 비교로 검증. PortOne 실연동 단계에서는
 * HMAC 서명 검증 어댑터로 교체한다.
 *
 * <p>결제 status 가 {@code PAID} 가 아닌 알림 (FAILED/CANCELLED 등) 은 충전하지 않고 ack 만 반환 —
 * PG 가 같은 알림을 다시 보내지 않도록 200 으로 받는다.
 */
@RestController
@RequestMapping("/webhooks/payments")
@Tag(name = "결제 webhook", description = "PG 결제 알림 수신. 같은 결제는 몇 번 와도 한 번만 충전.")
public class PaymentWebhookController {

    private static final String SECRET_HEADER = "X-Webhook-Secret";
    private static final String PAID_STATUS = "PAID";
    private static final String REFERENCE_TYPE = "PG_PAYMENT";

    private final ChargeCreditService chargeService;
    private final String configuredSecret;

    public PaymentWebhookController(
            ChargeCreditService chargeService,
            @Value("${payment.webhook.secret:}") String configuredSecret
    ) {
        this.chargeService = chargeService;
        this.configuredSecret = configuredSecret;
    }

    @PostMapping("/portone")
    @Operation(
            summary = "PortOne 결제 webhook 수신",
            description = """
                    PG 가 보낸 결제 알림으로 사용자 크레딧을 충전한다.

                    - {@code X-Webhook-Secret} 헤더가 환경변수 {@code PAYMENT_WEBHOOK_SECRET} 와
                      일치해야 한다 (timing-safe 비교).
                    - 같은 {@code paymentId} 로 다시 들어와도 잔액은 한 번만 증가하고 응답 result 는
                      {@code DUPLICATE} 로 반환된다 (멱등 보장).
                    - {@code status} 가 {@code PAID} 가 아니면 충전하지 않고 result {@code IGNORED}.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정상 수신. result 로 처리 종류(CHARGED/DUPLICATE/IGNORED) 구분."),
            @ApiResponse(responseCode = "400", description = "페이로드 검증 실패", content = @Content),
            @ApiResponse(responseCode = "401", description = "X-Webhook-Secret 누락 또는 불일치", content = @Content)
    })
    public PaymentWebhookResponse receive(
            @Valid @RequestBody PortOneWebhookRequest req,
            @Parameter(description = "환경변수 PAYMENT_WEBHOOK_SECRET 와 일치해야 함", required = true)
            @RequestHeader(value = SECRET_HEADER, required = false) String secret
    ) {
        verifySecret(secret);

        if (!PAID_STATUS.equalsIgnoreCase(req.status())) {
            return PaymentWebhookResponse.ignored();
        }

        ChargeResult result = chargeService.charge(new ChargeCommand(
                req.userId(),
                req.amount(),
                CreditType.CHARGE,
                REFERENCE_TYPE,
                req.paymentId(),
                "PG 결제 충전 (paymentId=" + req.paymentId() + ")"
        ));

        return result.idempotentSkip()
                ? PaymentWebhookResponse.duplicate(result.balance(), result.transactionId())
                : PaymentWebhookResponse.charged(result.balance(), result.transactionId());
    }

    private void verifySecret(String received) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            // 시크릿 미설정 → 외부 노출된 엔드포인트가 무방비. 명시적 거부.
            throw new PaymentWebhookAuthorizationException("payment webhook secret not configured");
        }
        if (received == null) {
            throw new PaymentWebhookAuthorizationException("missing " + SECRET_HEADER + " header");
        }
        // timing-safe 비교 — 시크릿 길이/내용 추측 공격 회피.
        byte[] a = received.getBytes(StandardCharsets.UTF_8);
        byte[] b = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(a, b)) {
            throw new PaymentWebhookAuthorizationException("invalid webhook secret");
        }
    }
}
