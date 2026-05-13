package dev.seedo.project.application;

import java.util.UUID;

/**
 * 프로젝트 소개 작성·공개를 LEADER 외 사용자가 시도 — 403 으로 매핑.
 *
 * <p>RBAC 시스템 권한 (PERM_*) 과 별개의 리소스 소유권 검증 — projects.leader_id 와 호출자 비교가 통과
 * 기준. ADMIN 강제 액션이 도입되면 별도 분기 (관리자 권한 가진 호출자는 우회) 필요할 수 있음.
 */
public class ProjectLeaderOnlyException extends RuntimeException {

    private final Long projectId;
    private final UUID actorId;

    public ProjectLeaderOnlyException(Long projectId, UUID actorId) {
        super("only project leader can perform this action: projectId=" + projectId + ", actor=" + actorId);
        this.projectId = projectId;
        this.actorId = actorId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public UUID getActorId() {
        return actorId;
    }
}
