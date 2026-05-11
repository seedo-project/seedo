package dev.seedo.idea.infrastructure;

/**
 * pgvector {@code vector(1536)} 컬럼은 Hibernate 기본 타입 매퍼에 없어 JPA 로 매핑하지 못한다.
 * 임베딩 upsert 는 native query 로 처리한다 (CLAUDE.md §11).
 *
 * <p>Spring Data 명명 규칙: 구현체는 {@code IdeaEmbeddingRepositoryImpl} — 자동 결합.
 */
public interface IdeaEmbeddingRepositoryCustom {

    /**
     * 임베딩 upsert. row 가 없으면 INSERT, 있으면 embedding 만 갱신 (keywords 는 보존).
     *
     * @param ideaId    대상 아이디어 id
     * @param embedding 차원이 컬럼 정의 ({@code vector(1536)}) 와 일치해야 한다 — 안 맞으면 Postgres 가 거부
     */
    void upsertEmbedding(long ideaId, float[] embedding);
}
