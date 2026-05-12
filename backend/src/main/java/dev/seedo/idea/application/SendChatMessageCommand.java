package dev.seedo.idea.application;

import java.util.UUID;

/**
 * 챗봇 메시지 전송 입력. {@code content} 검증 (blank) 은 service 진입에서 수행 — record compact constructor
 * 대신 service 안에서 ChatMessageEmptyException 으로 typed 매핑.
 */
public record SendChatMessageCommand(
        Long sessionId,
        UUID userId,
        String content
) {
}
