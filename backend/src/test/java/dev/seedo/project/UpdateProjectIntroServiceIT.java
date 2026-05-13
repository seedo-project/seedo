package dev.seedo.project;

import dev.seedo.project.application.ProjectIntroBlankFieldException;
import dev.seedo.project.application.ProjectLeaderOnlyException;
import dev.seedo.project.application.ProjectNotEditableException;
import dev.seedo.project.application.ProjectNotFoundException;
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
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프로젝트 소개 4 항목 부분 수정 서비스 IT (#140).
 *
 * <p>검증 포인트:
 * <ul>
 *   <li>LEADER 가 아닌 사용자는 {@link ProjectLeaderOnlyException} 으로 거부</li>
 *   <li>편집 가능 상태 (DRAFT, IN_PROGRESS) 외에는 {@link ProjectNotEditableException}</li>
 *   <li>null 인 필드는 기존 값 보존 (부분 수정)</li>
 *   <li>존재하지 않는 프로젝트는 {@link ProjectNotFoundException}</li>
 * </ul>
 */
@Transactional
class UpdateProjectIntroServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UpdateProjectIntroService service;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private ProjectRepository projectRepo;

    @Autowired
    private ProjectMemberRepository memberRepo;

    @Test
    void leader_can_set_all_four_fields() {
        Fixture f = setupDraft();

        Project updated = service.update(f.projectId, f.leader, new UpdateProjectIntroCommand(
                "https://storage.example/cover.png",
                "공부 습관 트래커",
                "타이머 + 통계로 학습 흐름 가시화",
                "## 가이드\n1. 타이머 시작"));

        assertThat(updated.getCoverImageUrl()).isEqualTo("https://storage.example/cover.png");
        assertThat(updated.getTitle()).isEqualTo("공부 습관 트래커");
        assertThat(updated.getDescription()).isEqualTo("타이머 + 통계로 학습 흐름 가시화");
        assertThat(updated.getGuideMd()).startsWith("## 가이드");
    }

    @Test
    void null_fields_preserve_existing_values() {
        Fixture f = setupDraft();
        service.update(f.projectId, f.leader, new UpdateProjectIntroCommand(
                null, "기존 제목", "기존 설명", null));

        // 두 번째 호출은 title 만 갱신, description / cover / guide 는 null 로 두면 보존되어야 한다.
        Project updated = service.update(f.projectId, f.leader, new UpdateProjectIntroCommand(
                null, "새 제목", null, null));

        assertThat(updated.getTitle()).isEqualTo("새 제목");
        assertThat(updated.getDescription()).as("null 인 필드는 기존 값 보존").isEqualTo("기존 설명");
        assertThat(updated.getCoverImageUrl()).isNull();
        assertThat(updated.getGuideMd()).isNull();
    }

    @Test
    void non_leader_is_rejected() {
        Fixture f = setupDraft();
        UUID outsider = UserFixture.create(userRepo);

        assertThatThrownBy(() -> service.update(f.projectId, outsider,
                new UpdateProjectIntroCommand(null, "탈취 시도", null, null)))
                .isInstanceOf(ProjectLeaderOnlyException.class);
    }

    @Test
    void editing_archived_project_is_rejected() {
        Fixture f = setupDraft();
        // DRAFT 상태에서 직접 archive — 도메인 메서드는 RECRUITING/IN_PROGRESS/COMPLETED 등 비 종료 상태 모두 허용.
        Project project = projectRepo.findById(f.projectId).orElseThrow();
        project.archive();
        projectRepo.saveAndFlush(project);

        assertThatThrownBy(() -> service.update(f.projectId, f.leader,
                new UpdateProjectIntroCommand(null, "박제 후 수정", null, null)))
                .isInstanceOfSatisfying(ProjectNotEditableException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(ProjectStatus.ARCHIVED));
    }

    @Test
    void blank_title_after_publish_is_rejected() {
        // publish 후 IN_PROGRESS 상태에서 title 을 공백으로 덮어 비우려는 우회 경로 (CodeRabbit #141).
        // 도메인 단 blank 가드 → 400 으로 매핑되는 ProjectIntroBlankFieldException.
        Fixture f = setupDraft();
        service.update(f.projectId, f.leader, new UpdateProjectIntroCommand(null, "t", "d", null));
        // 직접 publish 호출 대신 도메인 메서드 — fixture 한 줄 절약.
        projectRepo.findById(f.projectId).orElseThrow().publish();

        assertThatThrownBy(() -> service.update(f.projectId, f.leader,
                new UpdateProjectIntroCommand(null, "   ", null, null)))
                .isInstanceOf(ProjectIntroBlankFieldException.class);
    }

    @Test
    void blank_description_is_rejected() {
        Fixture f = setupDraft();

        assertThatThrownBy(() -> service.update(f.projectId, f.leader,
                new UpdateProjectIntroCommand(null, null, "", null)))
                .isInstanceOf(ProjectIntroBlankFieldException.class);
    }

    @Test
    void missing_project_throws_not_found() {
        UUID anyUser = UserFixture.create(userRepo);
        assertThatThrownBy(() -> service.update(999999L, anyUser,
                new UpdateProjectIntroCommand(null, "x", null, null)))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    private Fixture setupDraft() {
        UUID author = UserFixture.create(userRepo);
        UUID leader = UserFixture.create(userRepo);
        // projects.idea_id 는 FK 만 보면 되므로 DRAFT 아이디어로 충분 — 문서 셋업 불필요.
        Long ideaId = IdeaFixture.createDraft(ideaRepo, author, 10, 5).getId();
        Project p = ProjectFixture.createDraftWithLeader(projectRepo, memberRepo, ideaId, leader, "snapshot");
        return new Fixture(p.getId(), leader);
    }

    private record Fixture(Long projectId, UUID leader) {
    }
}
