package dev.seedo.idea.application;

import java.time.OffsetDateTime;

/**
 * 챗봇 메시지 전송 응답 — assistant 응답 한 줄. USER 메시지는 클라이언트가 이미 화면에 표시 중이라
 * 응답에 포함 안 해도 무방하지만, 일관성/디버깅 위해 assistant 메시지 메타만 명확히 반환.
 */
public record SendChatMessageResult(
        Long assistantMessageId,
        String content,
        OffsetDateTime createdAt
) {
}
