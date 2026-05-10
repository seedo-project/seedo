package dev.seedo.credit.application;

import dev.seedo.credit.domain.CreditType;

import java.util.UUID;

/**
 * 크레딧 잔액 변경 호출의 입력. 부호는 {@code amount} 그대로 사용한다 — 적립은 양수, 차감은 음수.
 *
 * <p>{@code referenceType} 와 {@code referenceId} 는 멱등성 키 — 둘 다 채워지면 같은 키로 들어온
 * 두 번째 호출은 idempotent skip 으로 처리된다 (PG webhook 자동 재시도 등). 둘 중 하나만 채워진 호출은
 * V1 의 partial UNIQUE 인덱스 / CHECK 와 호환되도록 도메인 진입에서 차단한다.
 */
public record ChargeCommand(
        UUID userId,
        long amount,
        CreditType type,
        String referenceType,
        String referenceId,
        String description
) {

    public ChargeCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (type == null) {
            throw new IllegalArgumentException("type required");
        }
        if (amount == 0) {
            throw new IllegalArgumentException("amount must not be zero");
        }
        if ((referenceType == null) != (referenceId == null)) {
            throw new IllegalArgumentException(
                    "referenceType and referenceId must be both null or both non-null");
        }
    }

    /** 멱등성 키가 부여된 호출인지. PG webhook 처럼 외부 재시도 가능 경로는 true. */
    public boolean hasReference() {
        return referenceType != null && referenceId != null;
    }
}
