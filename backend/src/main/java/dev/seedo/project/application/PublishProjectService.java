package dev.seedo.project.application;

import dev.seedo.project.domain.Project;
import dev.seedo.project.domain.ProjectStatus;
import dev.seedo.project.infrastructure.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 프로젝트를 DRAFT → IN_PROGRESS 로 공개 전이한다 (#140). 모집 흐름은 게시판으로 흡수하므로 RECRUITING
 * 단계를 건너뛴다 — IN_PROGRESS 가 "공개된 활성 프로젝트" 의미를 겸한다.
 *
 * <p>비관적 락: DRAFT → IN_PROGRESS 전이가 한 번만 일어나도록 idea row 락 (§8.3) 과 동일 패턴.
 * {@link ProjectRepository#findByIdForUpdate} 로 동시 publish 요청 직렬화. 두 번째 요청은 DRAFT 가 아니므로
 * {@link ProjectNotEditableException} 으로 거부.
 */
@Service
public class PublishProjectService {

    private final ProjectRepository projectRepo;

    public PublishProjectService(ProjectRepository projectRepo) {
        this.projectRepo = projectRepo;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Project publish(Long projectId, UUID actorId) {
        Project project = projectRepo.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (!project.getLeaderId().equals(actorId)) {
            throw new ProjectLeaderOnlyException(projectId, actorId);
        }

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ProjectNotEditableException(projectId, project.getStatus());
        }

        if (isBlank(project.getTitle()) || isBlank(project.getDescription())) {
            throw new ProjectPublishMissingFieldsException(projectId);
        }

        project.publish();
        return project;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
