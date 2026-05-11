package dev.seedo.idea;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.seedo.idea.domain.IdeaChatSession;
import dev.seedo.idea.infrastructure.IdeaChatSessionRepository;
import dev.seedo.support.AbstractIntegrationTest;
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
 * {@code POST /api/v1/chat-sessions/{id}/finalize} HTTP 통합 — JWT 인증 → @PreAuthorize → service →
 * ApiResponse 봉투까지 한 줄로 흐르는지 검증.
 *
 * <p>JwtDecoder 만 모킹 — JWKS 호출 우회. 나머지 필터/컨버터/메서드 보안/advice 는 모두 실제 빈.
 */
@AutoConfigureMockMvc
@Transactional
class ChatSessionFinalizeControllerIT extends AbstractIntegrationTest {

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
    private IdeaChatSessionRepository sessionRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void successful_finalize_returns_ok_envelope_with_ids() throws Exception {
        UUID user = createUserWithRole();
        Long sessionId = sessionRepo.saveAndFlush(new IdeaChatSession(user)).getId();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(user));

        mockMvc.perform(post("/api/v1/chat-sessions/" + sessionId + "/finalize")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("제목", "본문")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data.ideaId").isNumber())
                .andExpect(jsonPath("$.data.documentId").isNumber())
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void non_owner_returns_403_envelope() throws Exception {
        UUID owner = UserFixture.create(userRepo);
        UUID intruder = createUserWithRole();
        Long sessionId = sessionRepo.saveAndFlush(new IdeaChatSession(owner)).getId();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(intruder));

        mockMvc.perform(post("/api/v1/chat-sessions/" + sessionId + "/finalize")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("제목", "본문")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("not owned by caller")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void missing_session_returns_404_envelope() throws Exception {
        UUID user = createUserWithRole();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(user));

        mockMvc.perform(post("/api/v1/chat-sessions/999999/finalize")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("제목", "본문")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("chat session not found")));
    }

    @Test
    void missing_authorization_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/chat-sessions/1/finalize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("제목", "본문")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blank_title_returns_400() throws Exception {
        UUID user = createUserWithRole();
        Long sessionId = sessionRepo.saveAndFlush(new IdeaChatSession(user)).getId();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(user));

        mockMvc.perform(post("/api/v1/chat-sessions/" + sessionId + "/finalize")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "본문")))
                .andExpect(status().isBadRequest());
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
