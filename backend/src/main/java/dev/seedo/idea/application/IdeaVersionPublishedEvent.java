package dev.seedo.idea.application;

/**
 * 새 본문 버전이 트랜잭션 커밋 후 발행되었음을 알리는 도메인 이벤트. finalize (version=1) 와
 * 새 버전 발행 (version&gt;=2) 양쪽 모두 발행한다. 리스너는 임베딩 재계산을 트리거 —
 * 실제 OpenAI 호출은 다음 PR 에서 stub 자리에 채운다 (CLAUDE.md §8.4, issue #79).
 *
 * <p>리스너는 반드시 {@code @TransactionalEventListener(AFTER_COMMIT)} 로 받아 본 트랜잭션이
 * 롤백되면 임베딩 갱신도 일어나지 않게 한다.
 */
public record IdeaVersionPublishedEvent(
        long ideaId,
        long documentId,
        int version
) {
}
