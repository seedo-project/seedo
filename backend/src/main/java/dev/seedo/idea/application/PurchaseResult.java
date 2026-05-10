package dev.seedo.idea.application;

/**
 * 구매 성공 결과. {@code documentId} 는 산 시점 스냅샷 (CLAUDE.md §6.5) — 이후 새 버전이 발행돼도
 * 구매자는 이 id 의 본문을 계속 본다.
 */
public record PurchaseResult(
        long purchaseId,
        long balance,
        long documentId
) {
}
