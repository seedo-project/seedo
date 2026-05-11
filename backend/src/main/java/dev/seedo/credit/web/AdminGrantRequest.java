package dev.seedo.credit.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 관리자가 사용자에게 크레딧을 적립할 때 사용하는 요청 본문.
 * MVP 무료 크레딧 발급 경로 (PortOne 도입 전).
 */
@Schema(description = "관리자 크레딧 적립 요청")
public record AdminGrantRequest(
        @Schema(description = "적립 대상 사용자의 ID (users.id = auth.users.id, UUID)", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID userId,
        @Schema(description = "적립 크레딧 (양수)", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        @Positive long amount,
        @Schema(description = "적립 사유 (감사 로그용, 최대 255자)", example = "온보딩 크레딧 지급", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String reason
) {
}
