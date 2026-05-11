package dev.seedo.idea.application;

/** HTTP 매핑(404)은 web 레이어 {@code IdeaExceptionHandler}. */
public class ChatSessionNotFoundException extends RuntimeException {

    public ChatSessionNotFoundException(Long sessionId) {
        super("chat session not found: " + sessionId);
    }
}
