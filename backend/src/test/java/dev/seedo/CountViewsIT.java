package dev.seedo;

import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.project.domain.Project;
import dev.seedo.project.infrastructure.ProjectMemberRepository;
import dev.seedo.project.infrastructure.ProjectRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.IdeaFixture;
import dev.seedo.support.ProjectFixture;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V16 의 카운트 view (`idea_hype_counts`, `project_hype_counts`, `project_scrap_counts`) 가 underlying
 * 행 수와 일치하는지 검증한다 (#142).
 *
 * <p>RLS 동작 자체는 Supabase 환경 (`auth` 스키마 존재) 에서만 의미가 있어 testcontainers 에서는 no-op
 * 이지만, view 의 GROUP BY / count 정합성은 환경 독립적이므로 여기서 확인. supabase-js 직결 카운트
 * 조회가 정확히 동작할지의 1차 가드.
 *
 * <p>hypes / project_scraps 둘 다 백엔드에 JPA 엔티티가 없어 native query 로 INSERT·SELECT.
 */
@Transactional
class CountViewsIT extends AbstractIntegrationTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private ProjectRepository projectRepo;

    @Autowired
    private ProjectMemberRepository memberRepo;

    @Test
    void idea_hype_counts_reflects_underlying_rows() {
        UUID author = UserFixture.create(userRepo);
        UUID user1 = UserFixture.create(userRepo);
        UUID user2 = UserFixture.create(userRepo);
        Long ideaId = IdeaFixture.createDraft(ideaRepo, author, 10, 5).getId();

        insertIdeaHype(user1, ideaId);
        insertIdeaHype(user2, ideaId);

        long count = countFromView("idea_hype_counts", "idea_id", ideaId);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void project_hype_counts_reflects_underlying_rows() {
        UUID leader = UserFixture.create(userRepo);
        UUID hyper = UserFixture.create(userRepo);
        Long projectId = createDraftProject(leader);

        insertProjectHype(hyper, projectId);

        long count = countFromView("project_hype_counts", "project_id", projectId);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void project_scrap_counts_reflects_underlying_rows() {
        UUID leader = UserFixture.create(userRepo);
        UUID scrapper1 = UserFixture.create(userRepo);
        UUID scrapper2 = UserFixture.create(userRepo);
        Long projectId = createDraftProject(leader);

        insertScrap(scrapper1, projectId);
        insertScrap(scrapper2, projectId);

        long count = countFromView("project_scrap_counts", "project_id", projectId);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void empty_underlying_table_yields_no_view_row() {
        // 0 카운트일 때 view 가 별도 0 행을 만들지 않고 row 자체가 없다는 것 확인 — FE 가 absence 처리에
        // 의존할 수 있게 (`coalesce(count, 0)` 로 받는 흐름).
        UUID author = UserFixture.create(userRepo);
        Long ideaId = IdeaFixture.createDraft(ideaRepo, author, 10, 5).getId();

        Number rows = (Number) em.createNativeQuery(
                        "SELECT count(*) FROM idea_hype_counts WHERE idea_id = :ideaId")
                .setParameter("ideaId", ideaId)
                .getSingleResult();
        assertThat(rows.longValue()).isEqualTo(0L);
    }

    private Long createDraftProject(UUID leader) {
        UUID author = UserFixture.create(userRepo);
        Long ideaId = IdeaFixture.createDraft(ideaRepo, author, 10, 5).getId();
        Project p = ProjectFixture.createDraftWithLeader(projectRepo, memberRepo, ideaId, leader, "snapshot");
        return p.getId();
    }

    private void insertIdeaHype(UUID userId, Long ideaId) {
        em.createNativeQuery(
                        "INSERT INTO hypes (user_id, idea_id) VALUES (:userId, :ideaId)")
                .setParameter("userId", userId)
                .setParameter("ideaId", ideaId)
                .executeUpdate();
    }

    private void insertProjectHype(UUID userId, Long projectId) {
        em.createNativeQuery(
                        "INSERT INTO hypes (user_id, project_id) VALUES (:userId, :projectId)")
                .setParameter("userId", userId)
                .setParameter("projectId", projectId)
                .executeUpdate();
    }

    private void insertScrap(UUID userId, Long projectId) {
        em.createNativeQuery(
                        "INSERT INTO project_scraps (user_id, project_id) VALUES (:userId, :projectId)")
                .setParameter("userId", userId)
                .setParameter("projectId", projectId)
                .executeUpdate();
    }

    private long countFromView(String viewName, String entityCol, Long entityId) {
        // viewName / entityCol 은 테스트 코드 상수만 들어오므로 SQL injection 위험 없음 — 동적 키 컬럼명을
        // 바인딩 파라미터로 처리할 수 없어 문자열 결합 사용.
        Number result = (Number) em.createNativeQuery(
                        "SELECT count FROM " + viewName + " WHERE " + entityCol + " = :id")
                .setParameter("id", entityId)
                .getSingleResult();
        return result.longValue();
    }
}
