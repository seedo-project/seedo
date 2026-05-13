package dev.seedo.idea.infrastructure;

import dev.seedo.idea.application.IdeaSearchResult;

import java.util.List;

/**
 * pgvector {@code vector(1536)} 컬럼은 Hibernate 기본 타입 매퍼에 없어 JPA 로 매핑하지 못한다.
 * 임베딩 upsert / 검색은 native query 로 처리한다 (CLAUDE.md §11).
 *
 * <p>Spring Data 명명 규칙: 구현체는 {@code IdeaEmbeddingRepositoryImpl} — 자동 결합.
 */
public interface IdeaEmbeddingRepositoryCustom {

    /**
     * 임베딩 + 키워드 upsert. row 가 없으면 INSERT, 있으면 embedding 갱신.
     *
     * <p>{@code keywords} 가 비어있으면 (List.of()) embedding 만 갱신하고 기존 keywords 를 보존한다 —
     * 새 버전 발행(PublishIdeaVersionService) 은 키워드를 재추출하지 않으므로 finalize 가 박은 키워드를
     * 그대로 유지하기 위함. 비어있지 않으면 keywords 도 함께 EXCLUDED 로 덮는다.
     *
     * @param ideaId    대상 아이디어 id
     * @param embedding 차원이 컬럼 정의 ({@code vector(1536)}) 와 일치해야 한다 — 안 맞으면 Postgres 가 거부
     * @param keywords  카드 노출용 키워드. 빈 List 면 기존 보존
     */
    void upsertEmbedding(long ideaId, float[] embedding, List<String> keywords);

    /**
     * 쿼리 임베딩과 cosine 거리가 가까운 PUBLISHED 아이디어를 정렬해 반환.
     *
     * <p>필터: {@code ideas.status = 'PUBLISHED' AND deleted_at IS NULL}. 임베딩 row 없는 아이디어는
     * INNER JOIN 으로 자연 배제된다 — listener 실패로 임베딩이 비어있는 idea 도 검색에 안 나오는 게 안전.
     *
     * <p>반환되는 {@link IdeaSearchResult#score()} 는 {@code 1 - cosine_distance} (0~2 범위, 1 에 가까울수록 유사).
     * 하이브리드 검색의 한 입력으로 쓰일 땐 service 단계에서 RRF 점수로 덮어쓰여진다.
     *
     * @param queryEmbedding 차원 1536
     * @param limit          최대 결과 수. 호출자가 [1, 50] 범위를 보장해 보낸다.
     * @return cosine 거리 오름차순 (가까운 것 먼저). 비어있을 수 있다.
     */
    List<IdeaSearchResult> searchPublishedByEmbedding(float[] queryEmbedding, int limit);

    /**
     * 키워드 토큰들과 {@code idea_embeddings.keywords} 가 한 개라도 겹치는 PUBLISHED 아이디어를 정렬해 반환.
     *
     * <p>{@code &&} (array overlap) 로 GIN 인덱스 ({@code idx_idea_embeddings_keywords}) 활용. overlap 카운트
     * 내림차순 정렬 — 더 많이 겹친 카드가 위로. 동률은 idea_id 내림차순으로 안정 정렬 (테스트 가능성).
     *
     * <p>반환되는 {@link IdeaSearchResult#score()} 는 placeholder (overlap 카운트, 정수 → double). 임베딩
     * 경로와 함께 RRF 합산할 땐 service 가 RRF 점수로 덮어쓴다.
     *
     * @param tokens 토큰 리스트. 비어있지 않은 String 들. 비면 호출자가 호출 자체를 skip 해야 한다.
     * @param limit  최대 결과 수
     * @return overlap 카운트 내림차순. 비어있을 수 있다.
     */
    List<IdeaSearchResult> searchPublishedByKeywords(List<String> tokens, int limit);
}
