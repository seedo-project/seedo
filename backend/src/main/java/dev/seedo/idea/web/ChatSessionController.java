package dev.seedo.idea.web;

import dev.seedo.common.web.CurrentUserId;
import dev.seedo.idea.application.FinalizeChatSessionCommand;
import dev.seedo.idea.application.FinalizeChatSessionResult;
import dev.seedo.idea.application.FinalizeChatSessionService;
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
public class ChatSessionController {

    private final FinalizeChatSessionService service;

    public ChatSessionController(FinalizeChatSessionService service) {
        this.service = service;
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAuthority('PERM_IDEA_CREATE')")
    public FinalizeChatSessionResponse finalize(@PathVariable("id") Long sessionId,
                                                @CurrentUserId UUID userId,
                                                @Valid @RequestBody FinalizeChatSessionRequest req) {
        FinalizeChatSessionResult result = service.finalize(
                new FinalizeChatSessionCommand(sessionId, userId, req.title(), req.contentMd()));
        return new FinalizeChatSessionResponse(result.ideaId(), result.documentId(), result.version());
    }
}
