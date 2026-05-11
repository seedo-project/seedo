package dev.seedo.idea.application;

/**
 * 새 버전 발행 결과. {@code version} 은 새로 만든 row 의 version 번호 (MAX+1).
 */
public record PublishIdeaVersionResult(
        long ideaId,
        long documentId,
        int version
) {
}
