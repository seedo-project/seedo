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
 *
 * <p>keywords 는 두 SQL 로 분기 — 빈 List 면 embedding 만 갱신해 이전 키워드 보존, 비어있지 않으면
 * embedding + keywords 둘 다 갱신. INSERT 시점에는 양쪽 다 같은 컬럼 채움 (빈 List 는 빈 배열).
 */
@Repository
public class IdeaEmbeddingRepositoryImpl implements IdeaEmbeddingRepositoryCustom {

    /** keywords 도 함께 갱신 — finalize (이벤트에 keywords 채워짐) 경로. */
    private static final String UPSERT_WITH_KEYWORDS_SQL = """
            INSERT INTO idea_embeddings (idea_id, embedding, keywords)
            VALUES (:ideaId, CAST(:embedding AS vector), CAST(:keywords AS text[]))
            ON CONFLICT (idea_id) DO UPDATE
            SET embedding = EXCLUDED.embedding,
                keywords  = EXCLUDED.keywords
            """;

    /** embedding 만 갱신 — 새 버전 발행(PublishIdeaVersionService) 처럼 keywords 재추출 안 한 경로. */
    private static final String UPSERT_EMBEDDING_ONLY_SQL = """
            INSERT INTO idea_embeddings (idea_id, embedding, keywords)
            VALUES (:ideaId, CAST(:embedding AS vector), '{}'::text[])
            ON CONFLICT (idea_id) DO UPDATE
            SET embedding = EXCLUDED.embedding
            """;

    /**
     * cosine 거리 (`<=>`) 오름차순 정렬. ivfflat 인덱스가 활용되려면 ORDER BY 와 SELECT 의 거리식이
     * 동일해야 한다 — 두 곳 모두 같은 표현식 사용. keywords 는 카드 노출용 (페이지 구조 S201).
     */
    private static final String SEARCH_BY_EMBEDDING_SQL = """
            SELECT i.id,
                   i.author_id,
                   i.current_version_id,
                   i.price_credits,
                   i.reward_credits,
                   1 - (e.embedding <=> CAST(:queryVec AS vector)) AS score,
                   e.keywords
            FROM ideas i
            JOIN idea_embeddings e ON e.idea_id = i.id
            WHERE i.status = 'PUBLISHED' AND i.deleted_at IS NULL
            ORDER BY e.embedding <=> CAST(:queryVec AS vector)
            LIMIT :resultLimit
            """;

    /**
     * keywords 와 입력 토큰 배열의 overlap 카운트 내림차순. GIN 인덱스
     * ({@code idx_idea_embeddings_keywords}) 가 WHERE 의 {@code &&} 에 사용되고, ORDER BY 의
     * {@code cardinality(...)} 는 인덱스에 안 태워지지만 WHERE 가 후보를 충분히 줄여 큰 부담은 없다.
     *
     * <p>안정 정렬: overlap 동률은 {@code i.id DESC} (최신 PUBLISHED 우선) — IT 가 결정성을 가질 수 있게.
     */
    private static final String SEARCH_BY_KEYWORDS_SQL = """
            SELECT i.id,
                   i.author_id,
                   i.current_version_id,
                   i.price_credits,
                   i.reward_credits,
                   cardinality(ARRAY(SELECT unnest(e.keywords) INTERSECT SELECT unnest(CAST(:tokens AS text[]))))::double precision AS score,
                   e.keywords
            FROM ideas i
            JOIN idea_embeddings e ON e.idea_id = i.id
            WHERE i.status = 'PUBLISHED' AND i.deleted_at IS NULL
              AND e.keywords && CAST(:tokens AS text[])
            ORDER BY score DESC, i.id DESC
            LIMIT :resultLimit
            """;

    @PersistenceContext
    private EntityManager em;

    @Override
    public void upsertEmbedding(long ideaId, float[] embedding, List<String> keywords) {
        boolean hasKeywords = keywords != null && !keywords.isEmpty();
        String sql = hasKeywords ? UPSERT_WITH_KEYWORDS_SQL : UPSERT_EMBEDDING_ONLY_SQL;
        var query = em.createNativeQuery(sql)
                .setParameter("ideaId", ideaId)
                .setParameter("embedding", toVectorLiteral(embedding));
        if (hasKeywords) {
            query.setParameter("keywords", toTextArrayLiteral(keywords));
        }
        query.executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<IdeaSearchResult> searchPublishedByEmbedding(float[] queryEmbedding, int limit) {
        List<Object[]> rows = em.createNativeQuery(SEARCH_BY_EMBEDDING_SQL)
                .setParameter("queryVec", toVectorLiteral(queryEmbedding))
                .setParameter("resultLimit", limit)
                .getResultList();

        return rows.stream().map(IdeaEmbeddingRepositoryImpl::mapRow).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<IdeaSearchResult> searchPublishedByKeywords(List<String> tokens, int limit) {
        List<Object[]> rows = em.createNativeQuery(SEARCH_BY_KEYWORDS_SQL)
                .setParameter("tokens", toTextArrayLiteral(tokens))
                .setParameter("resultLimit", limit)
                .getResultList();

        return rows.stream().map(IdeaEmbeddingRepositoryImpl::mapRow).toList();
    }

    private static IdeaSearchResult mapRow(Object[] row) {
        // Postgres bigint -> Long, uuid -> UUID, int -> Integer, double precision -> Double, text[] -> String[].
        return new IdeaSearchResult(
                ((Number) row[0]).longValue(),
                (UUID) row[1],
                row[2] == null ? null : ((Number) row[2]).longValue(),
                ((Number) row[3]).intValue(),
                ((Number) row[4]).intValue(),
                ((Number) row[5]).doubleValue(),
                toKeywordList(row[6])
        );
    }

    private static List<String> toKeywordList(Object pgArray) {
        if (pgArray == null) {
            return List.of();
        }
        // PG JDBC 가 text[] 를 String[] 로 반환하는 게 일반 경로.
        if (pgArray instanceof String[] arr) {
            return List.of(arr);
        }
        // 일부 드라이버 / 환경은 java.sql.Array 로 반환할 수 있다 — instanceof 로 가드 후 풀어본다.
        // (가드 없이 캐스팅하면 String[] / java.sql.Array 둘 다 아닌 미지의 타입에서 ClassCastException.)
        if (pgArray instanceof java.sql.Array sqlArray) {
            try {
                Object inner = sqlArray.getArray();
                if (inner instanceof String[] arr) {
                    return List.of(arr);
                }
            } catch (java.sql.SQLException ignored) {
                // fall through — 빈 List 로 떨어짐
            }
        }
        return List.of();
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

    /**
     * Postgres text[] literal — {@code {a,b,c}} 형식. 키워드 안에 쉼표 / 큰따옴표 / 백슬래시가 있으면
     * 큰따옴표로 감싸고 escape. LLM 이 짧은 한국어 명사를 주는 흐름이라 대부분 escape 불필요.
     */
    private static String toTextArrayLiteral(List<String> items) {
        StringBuilder sb = new StringBuilder(items.size() * 16);
        sb.append('{');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quoteForArray(items.get(i)));
        }
        sb.append('}');
        return sb.toString();
    }

    private static String quoteForArray(String s) {
        // Postgres array literal 안에서는 큰따옴표 / 백슬래시를 escape. NULL 문자열은 호출 전에 필터링됨.
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
