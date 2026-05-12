package dev.seedo.credit.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * webhook 처리 결과. PG 입장에서는 200 ok 만 의미 있고, 본문은 운영자 디버깅용.
 *
 * <p>{@code result}:
 * <ul>
 *   <li>{@code CHARGED} — 새 결제 알림으로 잔액 적립</li>
 *   <li>{@code DUPLICATE} — 같은 paymentId 재수신, 멱등 처리 (잔액 변화 없음)</li>
 *   <li>{@code IGNORED} — status 가 PAID 가 아니라 충전하지 않음</li>
 * </ul>
 */
@Schema(description = "PG webhook 처리 결과")
public record PaymentWebhookResponse(
        @Schema(description = "처리 결과", example = "CHARGED", allowableValues = {"CHARGED", "DUPLICATE", "IGNORED"})
        String result,
        @Schema(description = "처리 후 잔액. CHARGED/DUPLICATE 일 때만 채워짐", example = "1000", nullable = true)
        Long balance,
        @Schema(description = "원장(credit_transactions) 레코드 ID. CHARGED/DUPLICATE 일 때만 채워짐", example = "2048", nullable = true)
        Long transactionId
) {

    public static PaymentWebhookResponse charged(long balance, long transactionId) {
        return new PaymentWebhookResponse("CHARGED", balance, transactionId);
    }

    public static PaymentWebhookResponse duplicate(long balance, long transactionId) {
        return new PaymentWebhookResponse("DUPLICATE", balance, transactionId);
    }

    public static PaymentWebhookResponse ignored() {
        return new PaymentWebhookResponse("IGNORED", null, null);
    }
}
