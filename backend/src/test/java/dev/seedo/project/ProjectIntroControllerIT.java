package dev.seedo.project;

import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.project.application.UpdateProjectIntroCommand;
import dev.seedo.project.application.UpdateProjectIntroService;
import dev.seedo.project.domain.Project;
import dev.seedo.project.infrastructure.ProjectMemberRepository;
import dev.seedo.project.infrastructure.ProjectRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.IdeaFixture;
import dev.seedo.support.ProjectFixture;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프로젝트 소개 컨트롤러 HTTP 통합 — JWT 인증, ApiResponse 봉투, ProjectExceptionHandler 의 4xx 매핑 검증 (#140).
 *
 * <p>RBAC 시스템 권한 (PERM_*) 은 안 쓰고 리소스 소유권 (LEADER) 만 검증 — 다른 사용자는 403, 익명은 401.
 */
@AutoConfigureMockMvc
@Transactional
class ProjectIntroControllerIT extends AbstractIntegrationTest {

    private static final String BEARER = "Bearer test-token";

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

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
    void leader_patches_intro_and_gets_ok_envelope() throws Exception {
        Fixture f = setupDraft();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(f.leader));

        mockMvc.perform(patch("/api/v1/projects/" + f.projectId + "/intro")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"공부 트래커","description":"학습 흐름 가시화"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.projectId").value(f.projectId))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.title").value("공부 트래커"))
                .andExpect(jsonPath("$.data.description").value("학습 흐름 가시화"));
    }

    @Test
    void publish_transitions_to_in_progress() throws Exception {
        Fixture f = setupDraft();
        // 사전 셋업: title + description 채워둠.
        updateService.update(f.projectId, f.leader,
                new UpdateProjectIntroCommand(null, "t", "d", null));
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(f.leader));

        mockMvc.perform(post("/api/v1/projects/" + f.projectId + "/publish")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void publish_without_required_fields_returns_400() throws Exception {
        Fixture f = setupDraft();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(f.leader));

        mockMvc.perform(post("/api/v1/projects/" + f.projectId + "/publish")
                        .header("Authorization", BEARER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("title and description")));
    }

    @Test
    void non_leader_patch_returns_403() throws Exception {
        Fixture f = setupDraft();
        UUID outsider = UserFixture.create(userRepo);
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(outsider));

        mockMvc.perform(patch("/api/v1/projects/" + f.projectId + "/intro")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"탈취 시도"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("only project leader")));
    }

    @Test
    void missing_project_returns_404() throws Exception {
        UUID anyUser = UserFixture.create(userRepo);
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(anyUser));

        mockMvc.perform(patch("/api/v1/projects/999999/intro")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("project not found")));
    }

    @Test
    void unauthenticated_patch_returns_401() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/1/intro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private Fixture setupDraft() {
        UUID author = UserFixture.create(userRepo);
        UUID leader = UserFixture.create(userRepo);
        Long ideaId = IdeaFixture.createDraft(ideaRepo, author, 10, 5).getId();
        Project p = ProjectFixture.createDraftWithLeader(projectRepo, memberRepo, ideaId, leader, "snapshot");
        return new Fixture(p.getId(), leader);
    }

    private Jwt jwtFor(UUID sub) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(sub.toString())
                .claim("iss", "https://test/auth/v1")
                .build();
    }

    private record Fixture(Long projectId, UUID leader) {
    }
}
