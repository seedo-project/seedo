package dev.seedo.idea.application;

import dev.seedo.idea.application.port.out.EmbeddingClient;
import dev.seedo.idea.infrastructure.IdeaEmbeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 자연어 쿼리를 임베딩으로 변환해 가까운 아이디어부터 정렬해 반환한다 (CLAUDE.md §11, §5.3).
 *
 * <p>흐름:
 * <ol>
 *   <li>쿼리 비어있음/공백 → {@link SearchQueryEmptyException}</li>
 *   <li>limit 정규화 — null/0/음수 → 기본 20, 상한 50 까지 클램프</li>
 *   <li>{@link EmbeddingClient} 로 쿼리 임베딩 추출 (외부 호출, 트랜잭션 외부)</li>
 *   <li>native query 로 ideas JOIN idea_embeddings cosine 거리 정렬</li>
 * </ol>
 *
 * <p>{@code @Transactional(readOnly = true)} — 검색은 read-only. 외부 호출(임베딩) 은 메서드 시작 직후
 * 트랜잭션 안에서 일어나지만, 호출 자체가 짧고 (수백 ms) 그 결과로 단일 native query 만 실행하므로
 * 트랜잭션 길이 부담 없음. 외부 실패 시 트랜잭션도 자연 롤백 — read-only 라 부수효과 없음.
 */
@Service
public class SearchIdeasService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private final EmbeddingClient embeddingClient;
    private final IdeaEmbeddingRepository embeddingRepo;

    public SearchIdeasService(EmbeddingClient embeddingClient, IdeaEmbeddingRepository embeddingRepo) {
        this.embeddingClient = embeddingClient;
        this.embeddingRepo = embeddingRepo;
    }

    @Transactional(readOnly = true)
    public List<IdeaSearchResult> search(String query, Integer requestedLimit) {
        if (query == null || query.isBlank()) {
            throw new SearchQueryEmptyException();
        }
        int limit = clampLimit(requestedLimit);
        float[] queryEmbedding = embeddingClient.embed(query.trim());
        return embeddingRepo.searchPublishedByEmbedding(queryEmbedding, limit);
    }

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
