package dev.seedo.idea.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "아이디어 새 버전 발행 요청 본문")
public record PublishIdeaVersionRequest(
        @Schema(description = "갱신할 아이디어 제목 (최대 200자)", example = "출퇴근 카풀 매칭 — v2 (운임 정산 추가)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String title,
        @Schema(description = "갱신할 마크다운 본문", example = "## 변경점\n- 운임 정산 흐름 추가\n...", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String contentMd
) {
}
