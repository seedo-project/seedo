package dev.seedo.project.application;

/**
 * publish 시점에 title / description 이 비어 있어 공개 불가 — 400 으로 매핑.
 *
 * <p>V15 의 chk_projects_published_fields 가 DB 단 최후 가드. 정상 흐름에서는 도메인 {@link
 * dev.seedo.project.domain.Project#publish()} 가 먼저 검증해 4xx 로 노출된다.
 */
public class ProjectPublishMissingFieldsException extends RuntimeException {

    private final Long projectId;

    public ProjectPublishMissingFieldsException(Long projectId) {
        super("project requires title and description before publish: projectId=" + projectId);
        this.projectId = projectId;
    }

    public Long getProjectId() {
        return projectId;
    }
}
