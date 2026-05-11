package dev.seedo.idea.application;

/**
 * finalize 결과. 클라이언트는 ideaId 로 작성한 아이디어의 상세 페이지로, documentId 로 첫 버전 본문을 식별한다.
 */
public record FinalizeChatSessionResult(
        long ideaId,
        long documentId,
        int version
) {
}
