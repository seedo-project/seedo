package dev.seedo.credit.web;

/**
 * webhook 시크릿 헤더 가드 실패. 401 로 매핑한다 ({@link PaymentWebhookExceptionHandler}).
 *
 * <p>발생 케이스:
 * <ul>
 *   <li>{@code PAYMENT_WEBHOOK_SECRET} 미설정 — 무방비 노출보다 명시적 거부가 안전</li>
 *   <li>{@code X-Webhook-Secret} 헤더 누락</li>
 *   <li>헤더 값이 설정값과 불일치</li>
 * </ul>
 */
public class PaymentWebhookAuthorizationException extends RuntimeException {
    public PaymentWebhookAuthorizationException(String message) {
        super(message);
    }
}
