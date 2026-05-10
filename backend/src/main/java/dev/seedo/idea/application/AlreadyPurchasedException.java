package dev.seedo.idea.application;

import java.util.UUID;

/**
 * 같은 사용자가 같은 아이디어를 두 번째 구매 시도한 경우. UNIQUE(idea_id, buyer_id) 가 마지막 방어선이지만
 * 정상 클릭 흐름에서는 이 service 단계의 사전 체크가 잡는다 (CLAUDE.md §8.2 step 1).
 *
 * <p>HTTP 매핑(409 Conflict)은 web 레이어 {@code IdeaExceptionHandler} 가 담당 — application 은 Spring Web 미의존.
 */
public class AlreadyPurchasedException extends RuntimeException {

    public AlreadyPurchasedException(Long ideaId, UUID buyerId) {
        super("already purchased: ideaId=" + ideaId + ", buyerId=" + buyerId);
    }
}
