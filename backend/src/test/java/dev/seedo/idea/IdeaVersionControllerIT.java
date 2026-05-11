package dev.seedo.idea;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.IdeaFixture;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/ideas/{id}/versions} HTTP 통합 — JWT → 권한 → service → ApiResponse 한 줄.
 * 소유권 / 상태 가드가 4xx 봉투로 정확히 노출되는지 확인.
 */
@AutoConfigureMockMvc
@Transactional
class IdeaVersionControllerIT extends AbstractIntegrationTest {

    private static final String BEARER = "Bearer test-token";

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository documentRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void successful_publish_returns_v2_in_envelope() throws Exception {
        UUID author = createUserWithRole();
        Long ideaId = IdeaFixture.createPublished(ideaRepo, documentRepo, author, 10, 5).getId();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(author));

        mockMvc.perform(post("/api/v1/ideas/" + ideaId + "/versions")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("v2 제목", "v2 본문")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.ideaId").value(ideaId))
                .andExpect(jsonPath("$.data.documentId").isNumber())
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void non_author_returns_403_envelope() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID intruder = createUserWithRole();
        Long ideaId = IdeaFixture.createPublished(ideaRepo, documentRepo, author, 10, 5).getId();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(intruder));

        mockMvc.perform(post("/api/v1/ideas/" + ideaId + "/versions")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("t", "c")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("not owned by caller")));
    }

    @Test
    void archived_idea_returns_400_envelope() throws Exception {
        UUID author = createUserWithRole();
        Idea seeded = IdeaFixture.createPublished(ideaRepo, documentRepo, author, 10, 5);
        seeded.archive();
        ideaRepo.saveAndFlush(seeded);
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(author));

        mockMvc.perform(post("/api/v1/ideas/" + seeded.getId() + "/versions")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("t", "c")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("not versionable")));
    }

    @Test
    void missing_idea_returns_404_envelope() throws Exception {
        UUID author = createUserWithRole();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(author));

        mockMvc.perform(post("/api/v1/ideas/999999/versions")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("t", "c")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("idea not found")));
    }

    @Test
    void missing_authorization_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/ideas/1/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("t", "c")))
                .andExpect(status().isUnauthorized());
    }

    private String body(String title, String contentMd) throws Exception {
        return mapper.writeValueAsString(Map.of("title", title, "contentMd", contentMd));
    }

    private Jwt jwtFor(UUID sub) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(sub.toString())
                .claim("iss", "https://test/auth/v1")
                .build();
    }

    private UUID createUserWithRole() {
        return UserFixture.createWithRole(userRepo, em, 1L);
    }
}
