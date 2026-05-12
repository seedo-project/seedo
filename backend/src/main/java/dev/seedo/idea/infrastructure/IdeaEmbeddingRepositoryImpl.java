package dev.seedo.idea.infrastructure;

import dev.seedo.idea.application.IdeaSearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * {@link IdeaEmbeddingRepositoryCustom} 구현. native query 로 pgvector upsert + 검색.
 *
 * <p>벡터 직렬화는 단순 문자열 캐스트 (`'[1.0, 2.0, ...]'::vector`). 1536 차원이면 약 10~15KB 텍스트인데
 * 한 트랜잭션에 한 번 호출이라 성능 문제 없음. PreparedStatement 의 PgObject 직렬화로 빼는 건 추후
 * `hibernate-vector` 모듈 도입 시 검토.
 *
 * <p>{@code ON CONFLICT (idea_id) DO UPDATE} 로 멱등성 — 같은 idea 에 대해 listener 가 두 번 실행돼도
 * 마지막 호출의 임베딩만 남는다. updated_at 트리거가 자동으로 갱신 시각 박는다.
 */
@Repository
public class IdeaEmbeddingRepositoryImpl implements IdeaEmbeddingRepositoryCustom {

    private static final String UPSERT_SQL = """
            INSERT INTO idea_embeddings (idea_id, embedding, keywords)
            VALUES (:ideaId, CAST(:embedding AS vector), '{}'::text[])
            ON CONFLICT (idea_id) DO UPDATE
            SET embedding = EXCLUDED.embedding
            """;

    /**
     * cosine 거리 (`<=>`) 오름차순 정렬. ivfflat 인덱스가 활용되려면 ORDER BY 와 SELECT 의 거리식이
     * 동일해야 한다 — 두 곳 모두 같은 표현식 사용.
     */
    private static final String SEARCH_SQL = """
            SELECT i.id,
                   i.author_id,
                   i.current_version_id,
                   i.price_credits,
                   i.reward_credits,
                   1 - (e.embedding <=> CAST(:queryVec AS vector)) AS similarity
            FROM ideas i
            JOIN idea_embeddings e ON e.idea_id = i.id
            WHERE i.status = 'PUBLISHED' AND i.deleted_at IS NULL
            ORDER BY e.embedding <=> CAST(:queryVec AS vector)
            LIMIT :resultLimit
            """;

    @PersistenceContext
    private EntityManager em;

    @Override
    public void upsertEmbedding(long ideaId, float[] embedding) {
        em.createNativeQuery(UPSERT_SQL)
                .setParameter("ideaId", ideaId)
                .setParameter("embedding", toVectorLiteral(embedding))
                .executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<IdeaSearchResult> searchPublishedByEmbedding(float[] queryEmbedding, int limit) {
        List<Object[]> rows = em.createNativeQuery(SEARCH_SQL)
                .setParameter("queryVec", toVectorLiteral(queryEmbedding))
                .setParameter("resultLimit", limit)
                .getResultList();

        return rows.stream().map(IdeaEmbeddingRepositoryImpl::mapRow).toList();
    }

    private static IdeaSearchResult mapRow(Object[] row) {
        // Postgres bigint -> Long, uuid -> UUID, int -> Integer, double precision -> Double.
        // PG JDBC 가 직접 변환하므로 캐스트 안전.
        return new IdeaSearchResult(
                ((Number) row[0]).longValue(),
                (UUID) row[1],
                row[2] == null ? null : ((Number) row[2]).longValue(),
                ((Number) row[3]).intValue(),
                ((Number) row[4]).intValue(),
                ((Number) row[5]).doubleValue()
        );
    }

    /** pgvector 의 문자열 입력 형식: {@code [v0,v1,...,vN]}. 공백/대괄호 외 다른 구분자 없음. */
    private static String toVectorLiteral(float[] vec) {
        StringBuilder sb = new StringBuilder(vec.length * 12);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
