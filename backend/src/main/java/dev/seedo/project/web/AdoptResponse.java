package dev.seedo.project.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 채택 응답. {@code rewardPaid=false} 인 경우 {@code rewardTransactionId} 는 null →
 * {@code @JsonInclude(NON_NULL)} 로 응답 본문에서 제거된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdoptResponse(
        long projectId,
        boolean rewardPaid,
        Long rewardTransactionId
) {
}
