package dev.seedo.project.application;

import dev.seedo.project.domain.Project;
import dev.seedo.project.domain.ProjectStatus;
import dev.seedo.project.infrastructure.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 프로젝트 소개 페이지 4 항목 (cover_image_url / title / description / guide_md) 을 LEADER 가 부분 수정한다 (#140).
 *
 * <p>가드 순서:
 * <ol>
 *   <li>프로젝트 존재 확인</li>
 *   <li>호출자가 LEADER 인지 확인 — 다른 사용자는 403</li>
 *   <li>편집 가능 상태 (DRAFT, IN_PROGRESS) 인지 — COMPLETED / ARCHIVED / DELETED 는 거부</li>
 *   <li>도메인 {@link Project#updateIntro} 호출 — null 필드는 기존 값 유지</li>
 * </ol>
 *
 * <p>비관적 락은 불필요 — 단일 row update 라 Hibernate dirty checking 으로 충분. 동시 수정은 마지막 writer
 * 가 이김 (last-write-wins). 분쟁 가드가 필요하면 추후 V16 에서 updated_at 기반 낙관적 락 추가.
 */
@Service
public class UpdateProjectIntroService {

    private final ProjectRepository projectRepo;

    public UpdateProjectIntroService(ProjectRepository projectRepo) {
        this.projectRepo = projectRepo;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Project update(Long projectId, UUID actorId, UpdateProjectIntroCommand cmd) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (!project.getLeaderId().equals(actorId)) {
            throw new ProjectLeaderOnlyException(projectId, actorId);
        }

        ProjectStatus status = project.getStatus();
        if (status != ProjectStatus.DRAFT && status != ProjectStatus.IN_PROGRESS) {
            throw new ProjectNotEditableException(projectId, status);
        }

        project.updateIntro(cmd.coverImageUrl(), cmd.title(), cmd.description(), cmd.guideMd());
        return project;
    }
}
