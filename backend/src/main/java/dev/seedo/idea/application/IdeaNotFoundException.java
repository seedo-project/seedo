package dev.seedo.idea.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class IdeaNotFoundException extends RuntimeException {

    public IdeaNotFoundException(Long ideaId) {
        super("idea not found: " + ideaId);
    }
}
