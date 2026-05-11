package dev.seedo.idea.web;

import dev.seedo.common.web.CurrentUserId;
import dev.seedo.idea.application.PublishIdeaVersionCommand;
import dev.seedo.idea.application.PublishIdeaVersionResult;
import dev.seedo.idea.application.PublishIdeaVersionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 아이디어 본문 새 버전 발행 엔드포인트. 실제 매핑은 {@code /api/v1/ideas/{id}/versions} —
 * {@code WebMvcConfig} 가 prefix 부착.
 *
 * <p>RBAC 은 {@code IDEA_CREATE} — 새 버전 발행도 본문 신규 작성과 동일한 의미 (작성자 권한).
 * 본인 아이디어 여부는 service 의 ownership 체크가 담당 (CLAUDE.md §9).
 */
@RestController
@RequestMapping("/ideas")
public class IdeaVersionController {

    private final PublishIdeaVersionService service;

    public IdeaVersionController(PublishIdeaVersionService service) {
        this.service = service;
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('PERM_IDEA_CREATE')")
    public PublishIdeaVersionResponse publishVersion(@PathVariable("id") Long ideaId,
                                                     @CurrentUserId UUID userId,
                                                     @Valid @RequestBody PublishIdeaVersionRequest req) {
        PublishIdeaVersionResult result = service.publish(
                new PublishIdeaVersionCommand(ideaId, userId, req.title(), req.contentMd()));
        return new PublishIdeaVersionResponse(result.ideaId(), result.documentId(), result.version());
    }
}
