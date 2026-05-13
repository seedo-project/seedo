package dev.seedo.project.web;

import dev.seedo.project.domain.Project;
import dev.seedo.project.domain.ProjectStatus;

/**
 * 프로젝트 소개 갱신/공개 응답. 4 항목 + 상태를 함께 반환해 클라이언트가 한 번에 화면을 갱신할 수 있게.
 */
public record ProjectIntroResponse(
        Long projectId,
        ProjectStatus status,
        String coverImageUrl,
        String title,
        String description,
        String guideMd
) {

    public static ProjectIntroResponse from(Project p) {
        return new ProjectIntroResponse(
                p.getId(),
                p.getStatus(),
                p.getCoverImageUrl(),
                p.getTitle(),
                p.getDescription(),
                p.getGuideMd()
        );
    }
}
