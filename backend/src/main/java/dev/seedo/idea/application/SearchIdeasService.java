package dev.seedo.idea.application;

import dev.seedo.idea.application.port.out.EmbeddingClient;
import dev.seedo.idea.infrastructure.IdeaEmbeddingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 자연어 쿼리를 임베딩으로 변환해 가까운 아이디어부터 정렬해 반환한다 (CLAUDE.md §11, §5.3).
 *
 * <p>흐름:
 * <ol>
 *   <li>쿼리 비어있음/공백 → {@link SearchQueryEmptyException}</li>
 *   <li>limit 정규화 — null/0/음수 → 기본 20, 상한 50 까지 클램프</li>
 *   <li>{@link EmbeddingClient} 로 쿼리 임베딩 추출 — <b>트랜잭션 없이</b> 호출</li>
 *   <li>native query 로 ideas JOIN idea_embeddings cosine 거리 정렬</li>
 * </ol>
 *
 * <p>의도적으로 메서드에 {@code @Transactional} 을 붙이지 않는다 (CLAUDE.md backend §"외부 호출").
 * 외부 LLM 호출은 응답까지 수백 ms ~ 수 초 — 그동안 DB 커넥션을 점유하면 동시 검색이 늘 때 커넥션 풀이
 * 고갈된다. 대신 임베딩 호출은 풀 밖에서 끝내고, 그 결과로만 단일 native query 를 호출한다
 * (Hibernate 가 자동으로 짧은 트랜잭션을 시작하고 닫는다 — read 한 줄이라 정합성 부담 없음).
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
