package dev.seedo.idea.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이디어 구매 결과")
public record PurchaseResponse(
        @Schema(description = "생성된 구매 레코드 ID", example = "1024")
        long purchaseId,
        @Schema(description = "구매 직후 사용자 잔액(크레딧)", example = "90")
        long balance,
        @Schema(description = "구매 시점의 본문 문서 ID (idea_documents.id 스냅샷)", example = "315")
        long documentId
) {
}
