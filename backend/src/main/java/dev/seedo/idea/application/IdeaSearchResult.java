package dev.seedo.idea.application;

import java.util.List;
import java.util.UUID;

/**
 * 자연어 검색 결과 한 줄. 본문은 비포함 — 본문은 구매자만 열람 (RLS + Spring 모두 일관).
 *
 * <p>{@code score} 는 하이브리드 검색의 결합 점수 — 의미 유사도 (pgvector cosine) 와 키워드 매칭 (GIN
 * overlap) 두 순위 리스트를 RRF (Reciprocal Rank Fusion, k=60) 로 합산한 값이다. 절대값 자체는
 * 해석하지 말고 정렬 순서만 사용 — 결과 리스트는 검색 시점에 이미 score 내림차순으로 정렬되어 온다.
 *
 * <p>{@code keywords} 는 카드 노출용 (페이지 구조 S201 — 카드는 키워드 칩만 보여주고, 클릭 시 크레딧
 * 지불 모달을 거쳐 본문 열람). finalize 시 LLM 이 추출, listener 가 {@code idea_embeddings.keywords} 에 upsert.
 */
public record IdeaSearchResult(
        Long ideaId,
        UUID authorId,
        Long currentVersionId,
        int priceCredits,
        int rewardCredits,
        double score,
        List<String> keywords
) {
}
