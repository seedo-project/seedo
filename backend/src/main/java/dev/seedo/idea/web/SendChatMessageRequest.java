package dev.seedo.idea.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용자가 챗봇에게 보낼 한 메시지. 길이 상한은 OpenAI 토큰 한도가 진짜 제한이지만, 명백한 sanity 가드만
 * 박는다 — 8000 자는 평범한 단락 수준이라 흐름 막을 일 없음.
 */
@Schema(description = "챗봇 메시지 전송 요청")
public record SendChatMessageRequest(
        @Schema(description = "사용자 메시지 본문 (공백 불가, 최대 8000 자)", example = "공부 습관을 잡아주는 앱을 만들고 싶어요.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 8000) String content
) {
}
