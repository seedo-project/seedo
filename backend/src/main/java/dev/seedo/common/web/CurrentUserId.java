package dev.seedo.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드 파라미터에 붙여 JWT 의 {@code sub} (Supabase auth.users.id, UUID) 를 자동 주입받는다.
 * 보호된 엔드포인트만 사용 — JWT 가 없으면 예외 발생 (Spring Security 에서 이미 401 처리되므로 도달 불가).
 *
 * <p>사용 예:
 * <pre>
 *   {@code @PostMapping("/{id}/purchase")}
 *   public PurchaseResponse purchase(@PathVariable Long id, @CurrentUserId UUID buyerId) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
