package dev.seedo.support;

import dev.seedo.project.domain.Project;
import dev.seedo.project.domain.ProjectMember;
import dev.seedo.project.infrastructure.ProjectMemberRepository;
import dev.seedo.project.infrastructure.ProjectRepository;

import java.util.UUID;

/**
 * IT 에서 프로젝트 + 리더 멤버십 셋업을 표현하는 정적 헬퍼.
 *
 * <p>채택 트랜잭션 (AdoptIdeaServiceIT) 자체가 프로젝트를 만드는 경로를 검증하므로 셋업 헬퍼 사용은
 * 드물다 — 주로 다음 PR (RECRUITING/COMPLETE 전이) 에서 사용된다.
 */
public final class ProjectFixture {

    private ProjectFixture() {
    }

    /** DRAFT 프로젝트 + LEADER 멤버 한 명 셋업. */
    public static Project createDraftWithLeader(ProjectRepository projectRepo,
                                                ProjectMemberRepository memberRepo,
                                                Long ideaId, UUID leader, String snapshotMd) {
        Project project = projectRepo.saveAndFlush(Project.create(ideaId, leader, snapshotMd));
        memberRepo.saveAndFlush(ProjectMember.leader(project.getId(), leader));
        return project;
    }
}
