package dev.seedo.credit.application;

import java.util.UUID;

/**
 * 잔액이 부족해 SPEND/ADJUST(음수) 호출을 처리하지 못한 경우. {@code CreditAmount.balance(...)} 가 제일
 * 먼저 잡고, {@link ChargeCreditService} 가 typed 예외로 변환해 caller 가 4xx 로 매핑할 수 있게 한다.
 *
 * <p>HTTP 매핑(400)은 web 레이어의 ControllerAdvice 가 담당 — application 은 Spring Web 미의존.
 */
public class InsufficientCreditException extends RuntimeException {

    private final UUID userId;
    private final long currentBalance;
    private final long requestedDelta;

    public InsufficientCreditException(UUID userId, long currentBalance, long requestedDelta) {
        super("insufficient credit: userId=" + userId
                + ", balance=" + currentBalance + ", delta=" + requestedDelta);
        this.userId = userId;
        this.currentBalance = currentBalance;
        this.requestedDelta = requestedDelta;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getCurrentBalance() {
        return currentBalance;
    }

    public long getRequestedDelta() {
        return requestedDelta;
    }
}
