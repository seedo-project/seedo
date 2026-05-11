package dev.seedo.idea.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 새 본문 버전 발행 후 임베딩 재계산을 트리거할 stub. AFTER_COMMIT 으로 받아 본 트랜잭션이
 * 롤백되면 임베딩 갱신도 일어나지 않는다 (CLAUDE.md §8.4).
 *
 * <p>실제 OpenAI text-embedding-3-small 호출 + {@code idea_embeddings} upsert 는 다음 PR
 * (issue 별도) 에서 이 자리에 채운다. 지금은 의도와 진입점만 코드로 박아둔다 — listener 등록
 * 자체가 service IT 에서 검증된다.
 *
 * <p>이 클래스를 임시 search 모듈로 옮기지 않은 이유: 현재 PR 의 범위가 트랜잭션 무결성 + 이벤트
 * 발행까지인데, 별도 모듈로 빼면 빈 패키지를 만들게 된다. 실제 OpenAI 클라이언트가 들어올 때
 * {@code dev.seedo.search} 또는 {@code dev.seedo.ai} 로 함께 이사.
 */
@Component
public class IdeaEmbeddingRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(IdeaEmbeddingRefreshListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVersionPublished(IdeaVersionPublishedEvent event) {
        // TODO(#?): OpenAI text-embedding-3-small 호출 → idea_embeddings upsert. 실패 시 재시도 큐.
        log.info("idea version published — embedding refresh deferred to next PR. ideaId={}, documentId={}, version={}",
                event.ideaId(), event.documentId(), event.version());
    }
}
