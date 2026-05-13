package dev.seedo.project.application;

/**
 * 프로젝트 id 가 DB 에 존재하지 않음 — 404 로 매핑 ({@link dev.seedo.project.web.ProjectExceptionHandler}).
 */
public class ProjectNotFoundException extends RuntimeException {

    private final Long projectId;

    public ProjectNotFoundException(Long projectId) {
        super("project not found: " + projectId);
        this.projectId = projectId;
    }

    public Long getProjectId() {
        return projectId;
    }
}
