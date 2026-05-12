package dev.seedo.idea.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "챗봇 응답 메시지")
public record SendChatMessageResponse(
        @Schema(description = "저장된 assistant 메시지 ID", example = "2048")
        Long assistantMessageId,
        @Schema(description = "assistant 응답 본문", example = "어떤 부분에서 가장 자주 흐름이 끊기시나요?")
        String content,
        @Schema(description = "응답 생성 시각", example = "2026-05-13T10:15:30+09:00")
        OffsetDateTime createdAt
) {
}
