package dev.seedo.idea.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이디어 새 버전 발행 결과")
public record PublishIdeaVersionResponse(
        @Schema(description = "버전이 발행된 아이디어의 ID", example = "42")
        long ideaId,
        @Schema(description = "새로 생성된 본문 문서 ID (idea_documents 의 새 row)", example = "412")
        long documentId,
        @Schema(description = "새 버전 번호 (직전 MAX + 1)", example = "2")
        int version
) {
}
