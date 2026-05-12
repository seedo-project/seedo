package dev.seedo.credit.web;

import dev.seedo.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * webhook 시크릿 가드 실패 → 401. scope 은 {@link PaymentWebhookController} 가 속한
 * {@code dev.seedo.credit.web} — 같은 패키지의 다른 컨트롤러 (관리자 크레딧) 도 잡지만,
 * 그쪽은 이 예외를 던질 일이 없어 무관.
 *
 * <p>{@code @ResponseStatus} 대신 {@code ResponseEntity} 로 본문 + 상태 동시 반환 — advice 메서드에
 * {@code @ResponseStatus} 가 붙으면 본문이 누락되는 케이스 회피 (다른 advice 와 동일 패턴).
 */
@ControllerAdvice(basePackages = "dev.seedo.credit.web")
public class PaymentWebhookExceptionHandler {

    @ExceptionHandler(PaymentWebhookAuthorizationException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(PaymentWebhookAuthorizationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
    }
}
