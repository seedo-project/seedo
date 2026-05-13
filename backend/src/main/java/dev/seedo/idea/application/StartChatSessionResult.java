package dev.seedo.idea.application;

import java.time.OffsetDateTime;

/**
 * 챗봇 세션 생성 결과 (#163). 클라이언트는 sessionId 를 받아 첫 메시지 POST 부터 호출.
 */
public record StartChatSessionResult(Long sessionId, OffsetDateTime createdAt) {
}
