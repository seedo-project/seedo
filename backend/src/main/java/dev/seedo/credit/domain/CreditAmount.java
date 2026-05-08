package dev.seedo.credit.domain;

/**
 * 크레딧 변동량·잔액을 검증하는 Value Object.
 *
 * 정적 팩토리에서 의미별 부호 제약을 강제 — 잘못된 값이 DB CHECK 에 도달하기 전에
 * 도메인 진입 시점에서 즉시 실패시킨다 (DB CHECK + append-only 트리거는 2차 방어선).
 */
public record CreditAmount(long value) {

    public static CreditAmount positive(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + value);
        }
        return new CreditAmount(value);
    }

    public static CreditAmount negative(long value) {
        if (value >= 0) {
            throw new IllegalArgumentException("amount must be negative: " + value);
        }
        return new CreditAmount(value);
    }

    public static CreditAmount nonZero(long value) {
        if (value == 0) {
            throw new IllegalArgumentException("amount must not be zero");
        }
        return new CreditAmount(value);
    }

    public static CreditAmount balance(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("balance must be non-negative: " + value);
        }
        return new CreditAmount(value);
    }
}
