package dev.seedo.idea.application;

/**
 * 대화 메시지가 한 줄도 없는 세션을 finalize 시도 — 400 매핑 ({@link dev.seedo.idea.web.IdeaExceptionHandler}).
 *
 * <p>빈 컨텍스트로 LLM 을 호출하면 hallucinate 가능성 있고 사용자 의도와도 안 맞음 — 명시적 거부가 안전.
 * 한 줄이라도 대화가 있으면 LLM 이 짧은 입력 기반으로 정리 가능.
 */
public class EmptyChatHistoryException extends RuntimeException {
    public EmptyChatHistoryException(long sessionId) {
        super("chat session has no messages to finalize: sessionId=" + sessionId);
    }
}
