package dev.seedo.idea.application;

import java.util.UUID;

/**
 * finalize 호출 입력. 본문(markdown)은 이번 PR 에선 클라이언트가 직접 채워 보낸다 — gpt-4o-mini
 * 본문 생성은 다음 PR (CLAUDE.md §11, issue #79).
 */
public record FinalizeChatSessionCommand(
        long sessionId,
        UUID actor,
        String title,
        String contentMd
) {
}
