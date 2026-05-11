package dev.seedo.idea.application;

import dev.seedo.idea.domain.ChatSessionStatus;

/**
 * IN_PROGRESS 가 아닌 세션에 finalize 호출. 이미 FINALIZED / ABANDONED 인 경우.
 * HTTP 매핑(400)은 web 레이어.
 */
public class ChatSessionNotFinalizableException extends RuntimeException {

    public ChatSessionNotFinalizableException(Long sessionId, ChatSessionStatus status) {
        super("chat session is not IN_PROGRESS: sessionId=" + sessionId + ", status=" + status);
    }
}
