package dev.seedo.idea.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * finalize 요청 본문. 이번 PR 에선 클라이언트가 마크다운을 직접 보낸다 — gpt-4o-mini 본문 생성은 다음 PR
 * (issue #79). title 길이 200 은 V2 의 {@code idea_documents.title varchar(200)} 과 동일.
 */
@Schema(description = "챗봇 세션 finalize 요청 본문")
public record FinalizeChatSessionRequest(
        @Schema(description = "발행할 아이디어 제목 (최대 200자)", example = "출퇴근 시간 카풀 매칭 앱", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String title,
        @Schema(description = "마크다운 본문", example = "## 배경\n...", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String contentMd
) {
}
