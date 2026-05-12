package dev.seedo.idea.application;

/** 챗봇 메시지 본문이 비어있거나 공백뿐 — 400 으로 매핑 ({@link dev.seedo.idea.web.IdeaExceptionHandler}). */
public class ChatMessageEmptyException extends RuntimeException {
    public ChatMessageEmptyException() {
        super("chat message content must not be blank");
    }
}
