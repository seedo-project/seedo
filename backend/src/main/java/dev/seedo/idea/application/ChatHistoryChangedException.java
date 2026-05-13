package dev.seedo.idea.application;

/**
 * finalize 가 LLM 호출 동안 새 메시지가 세션에 추가된 경우 — 409 매핑.
 *
 * <p>흐름: 사전 검증 시점에 history 5 개 → LLM 호출 (수 초) → INSERT 트랜잭션 진입 시 history 6 개.
 * 그대로 진행하면 마지막 메시지가 LLM draft 에 반영되지 않은 채 finalize 가 commit 된다.
 * 클라이언트는 새 메시지를 받은 뒤 다시 finalize 호출하면 된다.
 */
public class ChatHistoryChangedException extends RuntimeException {
    public ChatHistoryChangedException(long sessionId, long expectedSize, long actualSize) {
        super("chat history changed during finalize: sessionId=" + sessionId
                + ", expected=" + expectedSize + ", actual=" + actualSize);
    }
}
