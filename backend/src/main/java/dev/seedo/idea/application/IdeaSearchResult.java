package dev.seedo.idea.application;

import java.util.List;
import java.util.UUID;

/**
 * 자연어 검색 결과 한 줄. 본문은 비포함 — 본문은 구매자만 열람 (RLS + Spring 모두 일관).
 *
 * <p>{@code similarity} 는 1 - cosine_distance. pgvector {@code <=>} 가 0 (동일) ~ 2 (정반대) 범위를
 * 돌려주므로 1 에 가까울수록 가까운 결과. 정렬은 검색 시점에 끝났으므로 호출자는 점수 비교 없이
 * 그대로 순서대로 노출하면 된다.
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
        double similarity,
        List<String> keywords
) {
}
