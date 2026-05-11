package dev.seedo.idea.application;

import dev.seedo.idea.application.port.out.EmbeddingClient;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 새 본문 버전 발행 직후 임베딩을 비동기로 재계산해 {@code idea_embeddings} 에 upsert.
 * finalize / 새 버전 발행 트랜잭션이 commit 된 뒤 (`AFTER_COMMIT`) 동작 — 본 트랜잭션이 롤백되면
 * 임베딩 계산도 일어나지 않는다 (CLAUDE.md §8.4).
 *
 * <p>실패 처리 정책: 임베딩은 검색에만 쓰이는 부가 기능 — 사용자 흐름은 이미 commit 됐다. 따라서
 * OpenAI 호출 / DB 쓰기 실패는 WARN 로깅 후 drop. 트래픽 늘면 dead-letter 큐 + 재시도 잡 도입 (TODO).
 *
 * <p>{@link Propagation#REQUIRES_NEW} — AFTER_COMMIT 단계는 원 트랜잭션이 끝나 있으므로 새 트랜잭션이
 * 필요하다. 명시 안 하면 upsert 가 트랜잭션 없이 실행되어 connection 누수나 명시되지 않은 동작이 생긴다.
 */
@Component
public class IdeaEmbeddingRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(IdeaEmbeddingRefreshListener.class);

    private final IdeaDocumentRepository documentRepo;
    private final IdeaEmbeddingRepository embeddingRepo;
    private final EmbeddingClient embeddingClient;

    public IdeaEmbeddingRefreshListener(IdeaDocumentRepository documentRepo,
                                        IdeaEmbeddingRepository embeddingRepo,
                                        EmbeddingClient embeddingClient) {
        this.documentRepo = documentRepo;
        this.embeddingRepo = embeddingRepo;
        this.embeddingClient = embeddingClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVersionPublished(IdeaVersionPublishedEvent event) {
        try {
            IdeaDocument doc = documentRepo.findById(event.documentId())
                    .orElseThrow(() -> new IllegalStateException(
                            "document missing after commit: documentId=" + event.documentId()));

            float[] embedding = embeddingClient.embed(doc.getContentMd());
            embeddingRepo.upsertEmbedding(event.ideaId(), embedding);

            log.info("idea embedding refreshed. ideaId={}, documentId={}, version={}, dim={}",
                    event.ideaId(), event.documentId(), event.version(), embedding.length);
        } catch (RuntimeException e) {
            // 부가 기능이므로 사용자 흐름은 영향 받지 않는다. 검색이 일시적으로 stale 해질 뿐.
            // 트래픽 / 정합성이 중요해지면 dead-letter 큐 + 재시도 잡 도입.
            log.warn("idea embedding refresh failed — drop. ideaId={}, documentId={}, version={}, cause={}",
                    event.ideaId(), event.documentId(), event.version(), e.toString());
        }
    }
}
