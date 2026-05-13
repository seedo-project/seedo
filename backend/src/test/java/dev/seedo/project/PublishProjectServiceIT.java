package dev.seedo.project;

import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.project.application.ProjectLeaderOnlyException;
import dev.seedo.project.application.ProjectNotEditableException;
import dev.seedo.project.application.ProjectPublishMissingFieldsException;
import dev.seedo.project.application.PublishProjectService;
import dev.seedo.project.application.UpdateProjectIntroCommand;
import dev.seedo.project.application.UpdateProjectIntroService;
import dev.seedo.project.domain.Project;
import dev.seedo.project.domain.ProjectStatus;
import dev.seedo.project.infrastructure.ProjectMemberRepository;
import dev.seedo.project.infrastructure.ProjectRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.IdeaFixture;
import dev.seedo.support.ProjectFixture;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프로젝트 공개 전이 (DRAFT → IN_PROGRESS) 서비스 IT (#140).
 *
 * <p>검증 포인트:
 * <ul>
 *   <li>title + description 채워진 DRAFT 만 공개 가능 → 상태 IN_PROGRESS</li>
 *   <li>필수 필드 누락 시 {@link ProjectPublishMissingFieldsException}</li>
 *   <li>이미 IN_PROGRESS 면 {@link ProjectNotEditableException} (재발행 금지)</li>
 *   <li>LEADER 외 사용자 → {@link ProjectLeaderOnlyException}</li>
 * </ul>
 */
@Transactional
class PublishProjectServiceIT extends AbstractIntegrationTest {

    @Autowired
    private PublishProjectService publishService;

    @Autowired
    private UpdateProjectIntroService updateService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private ProjectRepository projectRepo;

    @Autowired
    private ProjectMemberRepository memberRepo;

    @Test
    void publish_transitions_draft_to_in_progress_when_required_fields_present() {
        Fixture f = setupDraftWithRequiredFields("t", "d");

        Project published = publishService.publish(f.projectId, f.leader);

        assertThat(published.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(published.getTitle()).isEqualTo("t");
        assertThat(published.getDescription()).isEqualTo("d");
    }

    @Test
    void publish_without_title_is_rejected() {
        Fixture f = setupDraft();
        // description 만 채우고 title 은 비워둠.
        updateService.update(f.projectId, f.leader,
                new UpdateProjectIntroCommand(null, null, "설명만", null));

        assertThatThrownBy(() -> publishService.publish(f.projectId, f.leader))
                .isInstanceOf(ProjectPublishMissingFieldsException.class);
    }

    @Test
    void publish_without_description_is_rejected() {
        Fixture f = setupDraft();
        updateService.update(f.projectId, f.leader,
                new UpdateProjectIntroCommand(null, "제목만", null, null));

        assertThatThrownBy(() -> publishService.publish(f.projectId, f.leader))
                .isInstanceOf(ProjectPublishMissingFieldsException.class);
    }

    @Test
    void republish_after_in_progress_is_rejected() {
        Fixture f = setupDraftWithRequiredFields("t", "d");
        publishService.publish(f.projectId, f.leader);

        assertThatThrownBy(() -> publishService.publish(f.projectId, f.leader))
                .isInstanceOfSatisfying(ProjectNotEditableException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS));
    }

    @Test
    void publish_by_non_leader_is_rejected() {
        Fixture f = setupDraftWithRequiredFields("t", "d");
        UUID outsider = UserFixture.create(userRepo);

        assertThatThrownBy(() -> publishService.publish(f.projectId, outsider))
                .isInstanceOf(ProjectLeaderOnlyException.class);
    }

    private Fixture setupDraft() {
        UUID author = UserFixture.create(userRepo);
        UUID leader = UserFixture.create(userRepo);
        Long ideaId = IdeaFixture.createDraft(ideaRepo, author, 10, 5).getId();
        Project p = ProjectFixture.createDraftWithLeader(projectRepo, memberRepo, ideaId, leader, "snapshot");
        return new Fixture(p.getId(), leader);
    }

    private Fixture setupDraftWithRequiredFields(String title, String description) {
        Fixture f = setupDraft();
        updateService.update(f.projectId, f.leader,
                new UpdateProjectIntroCommand(null, title, description, null));
        return f;
    }

    private record Fixture(Long projectId, UUID leader) {
    }
}
