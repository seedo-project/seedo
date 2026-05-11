package dev.seedo.idea.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * {@link IdeaEmbeddingRepositoryCustom} 구현. native query 로 pgvector upsert.
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

    @PersistenceContext
    private EntityManager em;

    @Override
    public void upsertEmbedding(long ideaId, float[] embedding) {
        em.createNativeQuery(UPSERT_SQL)
                .setParameter("ideaId", ideaId)
                .setParameter("embedding", toVectorLiteral(embedding))
                .executeUpdate();
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
