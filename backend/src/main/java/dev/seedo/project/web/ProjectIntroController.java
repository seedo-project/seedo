package dev.seedo.project.web;

import dev.seedo.common.web.CurrentUserId;
import dev.seedo.project.application.PublishProjectService;
import dev.seedo.project.application.UpdateProjectIntroCommand;
import dev.seedo.project.application.UpdateProjectIntroService;
import dev.seedo.project.domain.Project;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 프로젝트 소개 페이지 — LEADER 가 4 항목 작성 + 공개 (#140). 실제 매핑은 {@code /api/v1/projects/{id}/...}.
 *
 * <p>RBAC 시스템 권한 (PERM_*) 보다 리소스 소유권 (projects.leader_id == 호출자) 가 가드. 그래서
 * {@code @PreAuthorize("isAuthenticated()")} 만 두고 LEADER 검증은 service 에서 한다.
 *
 * <p>스크랩 토글은 Supabase 직결 (V12 hypes 와 동일 패턴) — Spring 엔드포인트 없음.
 */
@RestController
@RequestMapping("/projects")
@PreAuthorize("isAuthenticated()")
@Tag(name = "프로젝트 소개", description = "프로젝트 표지 4 항목 (대표이미지 / 제목 / 설명 / 가이드) 작성 + 공개.")
public class ProjectIntroController {

    private final UpdateProjectIntroService updateService;
    private final PublishProjectService publishService;

    public ProjectIntroController(UpdateProjectIntroService updateService,
                                  PublishProjectService publishService) {
        this.updateService = updateService;
        this.publishService = publishService;
    }

    @PatchMapping("/{id}/intro")
    @Operation(
            summary = "프로젝트 소개 부분 수정",
            description = """
                    LEADER 가 4 항목을 부분 수정한다. null 인 필드는 기존 값을 보존.

                    - 권한: 프로젝트 leader_id == 호출자 (다른 사용자는 403).
                    - 상태: DRAFT 또는 IN_PROGRESS 에서만. COMPLETED / ARCHIVED / DELETED 는 409.
                    - 길이: title ≤ 200 자 / description ≤ 10,000 자 / guide_md ≤ 20,000 자 / cover_image_url ≤ 500.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 완료. 갱신된 4 항목 + status 반환."),
            @ApiResponse(responseCode = "400", description = "필드 길이 제약 위반 또는 publish 필수 필드 누락 (publish 액션 시)", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT 누락 또는 만료", content = @Content),
            @ApiResponse(responseCode = "403", description = "LEADER 가 아님", content = @Content),
            @ApiResponse(responseCode = "404", description = "프로젝트 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "편집 불가 상태 (COMPLETED / ARCHIVED / DELETED)", content = @Content)
    })
    public ProjectIntroResponse updateIntro(
            @Parameter(description = "수정할 프로젝트 ID", example = "42", required = true)
            @PathVariable("id") Long projectId,
            @Valid @RequestBody UpdateProjectIntroRequest request,
            @CurrentUserId UUID userId) {
        Project updated = updateService.update(
                projectId,
                userId,
                new UpdateProjectIntroCommand(
                        request.coverImageUrl(),
                        request.title(),
                        request.description(),
                        request.guideMd()
                ));
        return ProjectIntroResponse.from(updated);
    }

    @PostMapping("/{id}/publish")
    @Operation(
            summary = "프로젝트 공개 (DRAFT → IN_PROGRESS)",
            description = """
                    LEADER 가 명시적으로 공개 액션을 호출한다. 모집 흐름이 별도로 없어 RECRUITING 을 건너뛰고
                    바로 IN_PROGRESS 로 전이한다 (#140).

                    - 권한: LEADER 만.
                    - 상태: DRAFT 에서만. 이미 IN_PROGRESS / COMPLETED 면 409.
                    - 필수 필드: title + description (null/blank 거부, 400).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공개 완료. status 가 IN_PROGRESS 로 전이된 4 항목 반환."),
            @ApiResponse(responseCode = "400", description = "title 또는 description 미설정", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT 누락 또는 만료", content = @Content),
            @ApiResponse(responseCode = "403", description = "LEADER 가 아님", content = @Content),
            @ApiResponse(responseCode = "404", description = "프로젝트 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "DRAFT 가 아님", content = @Content)
    })
    public ProjectIntroResponse publish(
            @Parameter(description = "공개할 프로젝트 ID", example = "42", required = true)
            @PathVariable("id") Long projectId,
            @CurrentUserId UUID userId) {
        return ProjectIntroResponse.from(publishService.publish(projectId, userId));
    }
}
