package dev.seedo.project.application;

import dev.seedo.project.domain.ProjectStatus;

/**
 * 종료/박제된 프로젝트의 소개를 수정·공개하려고 시도 — 409 로 매핑.
 *
 * <p>편집 허용 상태: DRAFT (작성 중), IN_PROGRESS (공개 후 수정 허용). COMPLETED / ARCHIVED / DELETED 는 거부.
 */
public class ProjectNotEditableException extends RuntimeException {

    private final Long projectId;
    private final ProjectStatus status;

    public ProjectNotEditableException(Long projectId, ProjectStatus status) {
        super("project is not editable in status " + status + ": projectId=" + projectId);
        this.projectId = projectId;
        this.status = status;
    }

    public Long getProjectId() {
        return projectId;
    }

    public ProjectStatus getStatus() {
        return status;
    }
}
