package dev.seedo.idea.application;

import java.util.UUID;

/** 새 버전 발행 입력. */
public record PublishIdeaVersionCommand(
        long ideaId,
        UUID actor,
        String title,
        String contentMd
) {
}
