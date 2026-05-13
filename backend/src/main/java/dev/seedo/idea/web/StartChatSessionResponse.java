package dev.seedo.idea.web;

import java.time.OffsetDateTime;

/**
 * 챗봇 세션 생성 응답 (#163). 클라이언트는 sessionId 로 이후 메시지/finalize 호출을 라우팅한다.
 */
public record StartChatSessionResponse(Long sessionId, OffsetDateTime createdAt) {
}
