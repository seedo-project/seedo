package dev.seedo.idea.web;

import dev.seedo.common.web.CurrentUserId;
import dev.seedo.idea.application.FinalizeChatSessionCommand;
import dev.seedo.idea.application.FinalizeChatSessionResult;
import dev.seedo.idea.application.FinalizeChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 챗봇 세션 finalize 엔드포인트. 실제 매핑은 {@code /api/v1/chat-sessions/{id}/finalize} —
 * {@code WebMvcConfig} 가 prefix 부착.
 *
 * <p>RBAC 은 {@code IDEA_CREATE} — finalize 는 결과적으로 새 아이디어를 만든다.
 * 본인 세션 여부는 service 의 ownership 체크가 담당 (RBAC 외부, CLAUDE.md §9 "리소스 소유권").
 */
@RestController
@RequestMapping("/chat-sessions")
@Tag(name = "챗봇 세션", description = "AI 챗봇과의 대화 세션을 마무리하고 정형화된 아이디어를 발행한다.")
public class ChatSessionController {

    private final FinalizeChatSessionService service;

    public ChatSessionController(FinalizeChatSessionService service) {
        this.service = service;
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAuthority('PERM_IDEA_CREATE')")
    @Operation(
            summary = "챗봇 세션 finalize → 아이디어 발행",
            description = """
                    IN_PROGRESS 상태의 챗봇 세션을 FINALIZED 로 전이시키며 새 아이디어를 생성한다.

                    - `ideas` row INSERT (DRAFT) → `idea_documents` v1 INSERT → `ideas.current_version_id` 갱신
                      → 세션 상태 FINALIZED — 한 트랜잭션에서 처리된다.
                    - 다른 사용자의 세션은 finalize 할 수 없다 (403).
                    - 이미 FINALIZED 또는 ABANDONED 상태인 세션은 재처리 불가 (400).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "finalize 성공. 생성된 ideaId / documentId / version(=1) 을 반환."),
            @ApiResponse(responseCode = "400", description = "이미 FINALIZED / ABANDONED 인 세션 / 요청 본문 검증 실패", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT 누락 또는 만료", content = @Content),
            @ApiResponse(responseCode = "403", description = "본인 세션이 아님 / PERM_IDEA_CREATE 권한 없음", content = @Content),
            @ApiResponse(responseCode = "404", description = "해당 챗봇 세션이 존재하지 않음", content = @Content)
    })
    public FinalizeChatSessionResponse finalize(
            @Parameter(description = "finalize 할 챗봇 세션 ID", example = "31", required = true)
            @PathVariable("id") Long sessionId,
            @CurrentUserId UUID userId,
            @Valid @RequestBody FinalizeChatSessionRequest req) {
        FinalizeChatSessionResult result = service.finalize(
                new FinalizeChatSessionCommand(sessionId, userId, req.title(), req.contentMd()));
        return new FinalizeChatSessionResponse(result.ideaId(), result.documentId(), result.version());
    }
}
