package dev.seedo.idea.application;

import java.util.UUID;

/**
 * 자연어 검색 결과 한 줄. 본문은 비포함 — 본문은 구매자만 열람 (RLS + Spring 모두 일관).
 *
 * <p>{@code similarity} 는 1 - cosine_distance. pgvector {@code <=>} 가 0 (동일) ~ 2 (정반대) 범위를
 * 돌려주므로 1 에 가까울수록 가까운 결과. 정렬은 검색 시점에 끝났으므로 호출자는 점수 비교 없이
 * 그대로 순서대로 노출하면 된다.
 */
public record IdeaSearchResult(
        Long ideaId,
        UUID authorId,
        Long currentVersionId,
        int priceCredits,
        int rewardCredits,
        double similarity
) {
}
