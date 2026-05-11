package dev.seedo.idea.application;

import dev.seedo.idea.domain.IdeaStatus;

/**
 * ARCHIVED / DELETED 인 아이디어에 새 버전 발행을 시도. HTTP 매핑(400)은 web 레이어.
 * DRAFT / PUBLISHED 만 허용 — 작성자가 발행 전 다듬거나 발행 후 본문을 수정할 수 있어야 한다 (CLAUDE.md §8.4).
 */
public class IdeaNotVersionableException extends RuntimeException {

    public IdeaNotVersionableException(Long ideaId, IdeaStatus status) {
        super("idea is not versionable: ideaId=" + ideaId + ", status=" + status);
    }
}
