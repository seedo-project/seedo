package dev.seedo.project.application;

import dev.seedo.idea.domain.IdeaStatus;

/**
 * PUBLISHED 가 아닌 아이디어에 채택 시도. HTTP 매핑(400)은 web 레이어.
 * DRAFT / ARCHIVED / DELETED 모두 거부.
 */
public class IdeaNotAdoptableException extends RuntimeException {

    public IdeaNotAdoptableException(Long ideaId, IdeaStatus status) {
        super("idea is not adoptable: ideaId=" + ideaId + ", status=" + status);
    }
}
