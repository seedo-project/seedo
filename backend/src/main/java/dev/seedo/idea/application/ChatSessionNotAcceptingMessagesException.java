package dev.seedo.idea.application;

import dev.seedo.idea.domain.ChatSessionStatus;

/**
 * 세션이 IN_PROGRESS 가 아닌 상태 (FINALIZED / ABANDONED) 에서 메시지 추가 시도 — 409 매핑.
 */
public class ChatSessionNotAcceptingMessagesException extends RuntimeException {
    public ChatSessionNotAcceptingMessagesException(Long sessionId, ChatSessionStatus currentStatus) {
        super("chat session is not accepting messages: sessionId=" + sessionId
                + ", status=" + currentStatus);
    }
}
