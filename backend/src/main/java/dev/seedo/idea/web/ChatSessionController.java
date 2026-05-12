package dev.seedo.idea.web;

import dev.seedo.common.web.CurrentUserId;
import dev.seedo.idea.application.FinalizeChatSessionCommand;
import dev.seedo.idea.application.FinalizeChatSessionResult;
import dev.seedo.idea.application.FinalizeChatSessionService;
import dev.seedo.idea.application.SendChatMessageCommand;
import dev.seedo.idea.application.SendChatMessageResult;
import dev.seedo.idea.application.SendChatMessageService;
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
 * 챗봇 세션 엔드포인트 — 메시지 전송 / finalize. 실제 매핑은 {@code /api/v1/chat-sessions/{id}/...} —
 * {@code WebMvcConfig} 가 prefix 부착.
 *
 * <p>RBAC 은 {@code IDEA_CREATE} — 챗봇 대화는 결과적으로 새 아이디어 작성 흐름의 일부.
 * 본인 세션 여부는 service 의 ownership 체크가 담당 (RBAC 외부, CLAUDE.md §9 "리소스 소유권").
 */
@RestController
@RequestMapping("/chat-sessions")
@Tag(name = "챗봇 세션", description = "AI 챗봇과의 대화 세션 — 메시지 전송 + 마무리(finalize) 시 아이디어 발행.")
public class ChatSessionController {

    private final FinalizeChatSessionService finalizeService;
    private final SendChatMessageService sendService;

    public ChatSessionController(FinalizeChatSessionService finalizeService,
                                 SendChatMessageService sendService) {
        this.finalizeService = finalizeService;
        this.sendService = sendService;
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAuthority('PERM_IDEA_CREATE')")
    @Operation(
            summary = "챗봇 한 turn 응답 받기",
            description = """
                    사용자 메시지를 받아 LLM(gpt-4o-mini) 에게 지금까지의 대화 + 새 메시지를 한꺼번에
                    컨텍스트로 보내고, 응답 한 덩어리를 받아 저장한 뒤 돌려준다.

                    - 본인 세션만 접근 가능 (403).
                    - IN_PROGRESS 상태에서만 메시지 추가 가능. FINALIZED / ABANDONED 는 409.
                    - USER 메시지 + ASSISTANT 응답은 LLM 호출 성공 후 한 트랜잭션으로 저장 —
                      LLM 실패 시 USER 도 들어가지 않는다 (재시도하면 깨끗한 상태).
                    - 한 자씩 떨어지는 스트리밍(SSE) 은 별도 엔드포인트로 추후 추가.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "응답 성공. assistantMessageId / content / createdAt 반환."),
            @ApiResponse(responseCode = "400", description = "메시지 본문이 비어있거나 길이 초과", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT 누락 또는 만료", content = @Content),
            @ApiResponse(responseCode = "403", description = "본인 세션이 아님 / PERM_IDEA_CREATE 권한 없음", content = @Content),
            @ApiResponse(responseCode = "404", description = "해당 챗봇 세션이 존재하지 않음", content = @Content),
            @ApiResponse(responseCode = "409", description = "세션이 IN_PROGRESS 상태가 아님 (FINALIZED / ABANDONED)", content = @Content)
    })
    public SendChatMessageResponse sendMessage(
            @Parameter(description = "메시지 추가할 챗봇 세션 ID", example = "31", required = true)
            @PathVariable("id") Long sessionId,
            @CurrentUserId UUID userId,
            @Valid @RequestBody SendChatMessageRequest req) {
        SendChatMessageResult result = sendService.send(
                new SendChatMessageCommand(sessionId, userId, req.content()));
        return new SendChatMessageResponse(
                result.assistantMessageId(),
                result.content(),
                result.createdAt());
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
        FinalizeChatSessionResult result = finalizeService.finalize(
                new FinalizeChatSessionCommand(sessionId, userId, req.title(), req.contentMd()));
        return new FinalizeChatSessionResponse(result.ideaId(), result.documentId(), result.version());
    }
}
