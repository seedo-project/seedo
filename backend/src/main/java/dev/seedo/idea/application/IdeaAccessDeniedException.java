package dev.seedo.idea.application;

import java.util.UUID;

/**
 * 작성자 아닌 사용자가 아이디어 본문 새 버전 발행을 시도. HTTP 매핑(403)은 web 레이어.
 */
public class IdeaAccessDeniedException extends RuntimeException {

    public IdeaAccessDeniedException(Long ideaId, UUID actor) {
        super("idea is not owned by caller: ideaId=" + ideaId + ", actor=" + actor);
    }
}
