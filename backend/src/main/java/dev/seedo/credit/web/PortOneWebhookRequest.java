package dev.seedo.credit.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * PG (PortOne) webhook 페이로드. 같은 결제 알림이 네트워크 재시도로 두세 번 도착해도
 * {@code paymentId} 가 멱등성 키 ({@code reference_id}) 로 쓰여 한 번만 충전된다 (CLAUDE.md §6.7, §8.1).
 *
 * <p>실 PortOne webhook 페이로드는 {@code imp_uid} / {@code merchant_uid} / {@code status} 형식이며,
 * user/amount 는 우리 결제 row 에서 조회해 채우는 흐름이다. MVP 는 결제 row 자체가 없어
 * webhook 에 user/amount 를 직접 받는 구조로 시작하고, PortOne 본격 연동 시 이 DTO 와 컨트롤러를
 * 어댑터로 교체한다.
 */
@Schema(description = "PG 결제 webhook 페이로드 (MVP — PortOne 도입 시 실스펙으로 교체)")
public record PortOneWebhookRequest(
        @Schema(description = "PG 결제 식별자 — 같은 값으로 들어온 두 번째 호출은 멱등 처리", example = "pmt_2026_05_12_001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String paymentId,
        @Schema(description = "충전 대상 사용자 ID (UUID, users.id = auth.users.id)", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID userId,
        @Schema(description = "충전할 크레딧 (양수)", example = "1000", requiredMode = Schema.RequiredMode.REQUIRED)
        @Positive long amount,
        @Schema(description = "결제 상태. PAID 만 충전 처리, 그 외 (FAILED/CANCELLED 등) 는 ack 만.", example = "PAID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 32) String status
) {
}
