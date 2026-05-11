package dev.seedo.idea.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * finalize 요청 본문. 이번 PR 에선 클라이언트가 마크다운을 직접 보낸다 — gpt-4o-mini 본문 생성은 다음 PR
 * (issue #79). title 길이 200 은 V2 의 {@code idea_documents.title varchar(200)} 과 동일.
 */
public record FinalizeChatSessionRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String contentMd
) {
}
