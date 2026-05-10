package dev.seedo.idea.application;

import dev.seedo.idea.domain.IdeaStatus;

/**
 * 구매 가능 상태가 아닌 아이디어를 사려고 한 경우. PUBLISHED 만 구매 가능 — DRAFT/ARCHIVED/DELETED 차단.
 * HTTP 매핑(400)은 web 레이어 {@code IdeaExceptionHandler}.
 */
public class IdeaNotPurchasableException extends RuntimeException {

    public IdeaNotPurchasableException(Long ideaId, IdeaStatus actual) {
        super("idea not purchasable: ideaId=" + ideaId + ", status=" + actual);
    }
}
