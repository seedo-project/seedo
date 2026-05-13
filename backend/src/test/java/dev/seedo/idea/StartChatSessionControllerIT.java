package dev.seedo.idea;

import com.jayway.jsonpath.JsonPath;
import dev.seedo.idea.domain.ChatSessionStatus;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/chat-sessions} HTTP 통합 — JWT → @PreAuthorize(PERM_IDEA_CREATE) → 201 Created (#163).
 *
 * <p>클래스 레벨 {@code @Transactional} 미사용 — saveAndFlush 한 row 가 후속 SELECT 검증에서 실제 DB 에
 * 보여야 하므로 명시 commit ({@link TransactionTemplate}) 으로 fixture 셋업 (SendChatMessageControllerIT
 * 와 동일 패턴, lessons F.5).
 */
@AutoConfigureMockMvc
class StartChatSessionControllerIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/v1/chat-sessions";
    private static final String BEARER = "Bearer test-token";

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaChatSessionRepository sessionRepo;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    @Test
    void authenticated_user_creates_session_returns_201_with_db_row() throws Exception {
        UUID user = createUserWithRole();
        primeAuth(user);

        MvcResult result = mockMvc.perform(post(PATH).header("Authorization", BEARER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.sessionId").isNumber())
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andReturn();

        Long sessionId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.sessionId")).longValue();
        IdeaChatSession persisted = sessionRepo.findById(sessionId).orElseThrow();
        assertThat(persisted.getUserId()).isEqualTo(user);
        assertThat(persisted.getStatus()).isEqualTo(ChatSessionStatus.IN_PROGRESS);
        assertThat(persisted.getIdeaId()).as("새 세션은 아직 idea 와 연결 안 됨").isNull();
    }

    @Test
    void missing_authorization_returns_401() throws Exception {
        mockMvc.perform(post(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void user_without_role_returns_403() throws Exception {
        // role 부여 안 한 사용자 → PERM_IDEA_CREATE 권한 없음.
        UUID user = tx.execute(s -> UserFixture.create(userRepo));
        primeAuth(user);

        mockMvc.perform(post(PATH).header("Authorization", BEARER)).andExpect(status().isForbidden());
    }

    private void primeAuth(UUID userId) {
        when(jwtDecoder.decode("test-token")).thenReturn(jwtFor(userId));
    }

    private Jwt jwtFor(UUID sub) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(sub.toString())
                .claim("iss", "https://test/auth/v1")
                .build();
    }

    private UUID createUserWithRole() {
        return tx.execute(s -> UserFixture.createWithRole(userRepo, em, 1L));
    }
}
