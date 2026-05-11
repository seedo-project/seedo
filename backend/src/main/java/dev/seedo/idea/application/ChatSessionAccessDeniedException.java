package dev.seedo.idea.application;

import java.util.UUID;

/**
 * 본인이 아닌 사용자가 챗봇 세션을 finalize 시도. HTTP 매핑(403)은 web 레이어.
 * 세션 존재 자체를 노출하지 않으려면 404 도 선택지지만, 현 단계에선 행위 구분 명확성을 우선.
 */
public class ChatSessionAccessDeniedException extends RuntimeException {

    public ChatSessionAccessDeniedException(Long sessionId, UUID actor) {
        super("chat session is not owned by caller: sessionId=" + sessionId + ", actor=" + actor);
    }
}
