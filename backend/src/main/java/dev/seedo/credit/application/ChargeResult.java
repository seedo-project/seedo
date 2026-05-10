package dev.seedo.credit.application;

/**
 * 크레딧 충전 결과.
 *
 * <p>{@code idempotentSkip == true} 면 같은 멱등성 키로 이미 처리된 호출이라 잔액·원장에 변화 없음.
 * 이 경우 {@code transactionId} 는 최초 처리 때 생긴 row 의 id 이고, {@code balance} 는 현재 잔액 (변화 없음).
 */
public record ChargeResult(
        long balance,
        long transactionId,
        boolean idempotentSkip
) {
}
