package dev.seedo.credit.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 관리자가 사용자에게 크레딧을 적립할 때 사용하는 요청 본문.
 * MVP 무료 크레딧 발급 경로 (PortOne 도입 전).
 */
public record AdminGrantRequest(
        @NotNull UUID userId,
        @Positive long amount,
        @NotBlank @Size(max = 255) String reason
) {
}
