package dev.seedo.idea.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "챗봇 세션 finalize 결과")
public record FinalizeChatSessionResponse(
        @Schema(description = "생성된 아이디어의 ID", example = "42")
        long ideaId,
        @Schema(description = "본문 v1 문서(idea_documents) 의 ID", example = "315")
        long documentId,
        @Schema(description = "본문 버전 번호 (finalize 직후이므로 항상 1)", example = "1")
        int version
) {
}
