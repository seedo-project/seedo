package dev.seedo.idea.web;

import dev.seedo.common.web.CurrentUserId;
import dev.seedo.idea.application.PublishIdeaVersionCommand;
import dev.seedo.idea.application.PublishIdeaVersionResult;
import dev.seedo.idea.application.PublishIdeaVersionService;
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
 * 아이디어 본문 새 버전 발행 엔드포인트. 실제 매핑은 {@code /api/v1/ideas/{id}/versions} —
 * {@code WebMvcConfig} 가 prefix 부착.
 *
 * <p>RBAC 은 {@code IDEA_CREATE} — 새 버전 발행도 본문 신규 작성과 동일한 의미 (작성자 권한).
 * 본인 아이디어 여부는 service 의 ownership 체크가 담당 (CLAUDE.md §9).
 */
@RestController
@RequestMapping("/ideas")
@Tag(name = "아이디어 버전", description = "공개 후에도 본문을 새 버전으로 발행한다. 기존 버전은 분쟁 방지용으로 보존된다.")
public class IdeaVersionController {

    private final PublishIdeaVersionService service;

    public IdeaVersionController(PublishIdeaVersionService service) {
        this.service = service;
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('PERM_IDEA_CREATE')")
    @Operation(
            summary = "아이디어 새 버전 발행",
            description = """
                    작성자가 본인 아이디어의 본문을 새 버전으로 갱신한다.

                    - 버전 번호는 자동 증가 (MAX(version)+1).
                    - `ideas.current_version_id` 가 새 버전 row 로 갱신된다.
                    - 기존 버전 row 는 보존되어 구매자가 산 시점 본문을 계속 열람 가능
                      (`idea_purchases.document_id` 스냅샷).
                    - 본인 아이디어만 새 버전 발행 가능 (403).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "새 버전 발행 성공. ideaId / 새 documentId / 새 version 을 반환."),
            @ApiResponse(responseCode = "400", description = "요청 본문 검증 실패 / 새 버전 발행 불가능한 아이디어 상태", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT 누락 또는 만료", content = @Content),
            @ApiResponse(responseCode = "403", description = "본인 아이디어가 아님 / PERM_IDEA_CREATE 권한 없음", content = @Content),
            @ApiResponse(responseCode = "404", description = "해당 아이디어가 존재하지 않음", content = @Content)
    })
    public PublishIdeaVersionResponse publishVersion(
            @Parameter(description = "새 버전을 발행할 아이디어 ID", example = "42", required = true)
            @PathVariable("id") Long ideaId,
            @CurrentUserId UUID userId,
            @Valid @RequestBody PublishIdeaVersionRequest req) {
        PublishIdeaVersionResult result = service.publish(
                new PublishIdeaVersionCommand(ideaId, userId, req.title(), req.contentMd()));
        return new PublishIdeaVersionResponse(result.ideaId(), result.documentId(), result.version());
    }
}
