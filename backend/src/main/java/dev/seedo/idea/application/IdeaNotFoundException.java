package dev.seedo.idea.application;

/**
 * HTTP 매핑(404)은 web 레이어 {@code IdeaExceptionHandler}.
 */
public class IdeaNotFoundException extends RuntimeException {

    public IdeaNotFoundException(Long ideaId) {
        super("idea not found: " + ideaId);
    }
}
