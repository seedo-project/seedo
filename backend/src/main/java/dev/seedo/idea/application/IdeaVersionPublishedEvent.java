package dev.seedo.idea.application;

import java.util.List;

/**
 * 새 본문 버전이 트랜잭션 커밋 후 발행되었음을 알리는 도메인 이벤트. finalize (version=1) 와
 * 새 버전 발행 (version&gt;=2) 양쪽 모두 발행한다. 리스너 ({@link IdeaEmbeddingRefreshListener}) 가
 * 임베딩 재계산 + 키워드 upsert 를 트리거.
 *
 * <p>리스너는 반드시 {@code @TransactionalEventListener(AFTER_COMMIT)} 로 받아 본 트랜잭션이
 * 롤백되면 임베딩 / 키워드 갱신도 일어나지 않게 한다.
 *
 * <p>{@code keywords}: finalize 만 LLM 으로 추출된 키워드를 채운다 (페이지 구조 S201 카드 노출용).
 * 새 버전 발행 (PublishIdeaVersionService) 은 빈 List 로 발행 — 리스너가 빈 키워드는 upsert 에서 skip,
 * 이전 버전의 키워드를 보존한다.
 */
public record IdeaVersionPublishedEvent(
        long ideaId,
        long documentId,
        int version,
        List<String> keywords
) {
}
