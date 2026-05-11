package dev.seedo.project.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 채택 응답. {@code rewardPaid=false} 인 경우 {@code rewardTransactionId} 는 null →
 * {@code @JsonInclude(NON_NULL)} 로 응답 본문에서 제거된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "아이디어 채택 결과. rewardPaid=false 일 때 rewardTransactionId 필드는 응답 본문에서 생략된다.")
public record AdoptResponse(
        @Schema(description = "생성된 프로젝트의 ID", example = "17")
        long projectId,
        @Schema(description = "작성자에게 보상이 지급되었는지 여부 (자가 채택 또는 이미 보상 지급된 아이디어면 false)", example = "true")
        boolean rewardPaid,
        @Schema(description = "보상 지급 시 생성된 원장 ID. rewardPaid=false 일 때는 응답에서 생략됨.", example = "3072", nullable = true)
        Long rewardTransactionId
) {
}
