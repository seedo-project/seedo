package dev.seedo.idea;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.seedo.idea.domain.ChatMessageRole;
import dev.seedo.idea.domain.IdeaChatMessage;
import dev.seedo.idea.domain.IdeaChatSession;
import dev.seedo.idea.infrastructure.IdeaChatMessageRepository;
import dev.seedo.idea.infrastructure.IdeaChatSessionRepository;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/chat-sessions/{id}/messages} HTTP 통합 — 인증, @PreAuthorize, service, advice 봉투까지
 * 한 줄로 흐르는지. {@link dev.seedo.idea.application.port.out.ChatClient} 는 stub 빈 (결정적 응답).
 *
 * <p>클래스 레벨 {@code @Transactional} — service 의 {@code TransactionTemplate.execute} 는 outer 테스트
 * 트랜잭션을 PROPAGATION_REQUIRED 로 join 하므로 INSERT 결과가 같은 트랜잭션에서 조회 가능. 단일 요청
 * 검증이라 lessons F.5 의 "멀티 트랜잭션 가시성" 시나리오에 해당하지 않음.
 */
@AutoConfigureMockMvc
@Transactional
class SendChatMessageControllerIT extends AbstractIntegrationTest {

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

    @Autowired
    private IdeaChatMessageRepository messageRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository docRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void successful_send_returns_assistant_reply_and_persists_both_messages() throws Exception {
        UUID user = createUserWithRole();
        Long sessionId = sessionRepo.saveAndFlush(new IdeaChatSession(user)).getId();
        primeAuth(user);

        mockMvc.perform(post(path(sessionId))
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("공부 습관 잡아주는 앱")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                // stub ChatClient 의 결정적 응답 (IntegrationTestStubsConfig)
                .andExpect(jsonPath("$.data.content").value("stub assistant reply"))
                .andExpect(jsonPath("$.data.assistantMessageId").isNumber())
                .andExpect(jsonPath("$.data.createdAt").isString());

        // USER + ASSISTANT 두 row, 순서대로
        List<IdeaChatMessage> stored = messageRepo.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId);
        assertThat(stored).hasSize(2);
        assertThat(stored.get(0).getRole()).isEqualTo(ChatMessageRole.USER);
        assertThat(stored.get(0).getContent()).isEqualTo("공부 습관 잡아주는 앱");
        assertThat(stored.get(1).getRole()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(stored.get(1).getContent()).isEqualTo("stub assistant reply");
    }

    @Test
    void non_owner_returns_403_and_does_not_persist() throws Exception {
        UUID owner = UserFixture.create(userRepo);
        UUID intruder = createUserWithRole();
        Long sessionId = sessionRepo.saveAndFlush(new IdeaChatSession(owner)).getId();
        primeAuth(intruder);

        mockMvc.perform(post(path(sessionId))
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ping")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("not owned by caller")));

        assertThat(messageRepo.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId)).isEmpty();
    }

    @Test
    void missing_session_returns_404() throws Exception {
        UUID user = createUserWithRole();
        primeAuth(user);

        mockMvc.perform(post(path(999_999L))
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ping")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("chat session not found")));
    }

    @Test
    void finalized_session_returns_409_and_does_not_persist() throws Exception {
        UUID user = createUserWithRole();
        // idea_chat_sessions.idea_id 는 ideas 에 FK — 실제 row 만들어 셋업.
        Long ideaId = IdeaFixture.createPublished(ideaRepo, docRepo, user, 10, 5).getId();
        IdeaChatSession session = new IdeaChatSession(user);
        session.finalize(ideaId);
        Long sessionId = sessionRepo.saveAndFlush(session).getId();
        primeAuth(user);

        mockMvc.perform(post(path(sessionId))
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ping")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("not accepting messages")));

        assertThat(messageRepo.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId)).isEmpty();
    }

    @Test
    void blank_content_returns_400() throws Exception {
        UUID user = createUserWithRole();
        Long sessionId = sessionRepo.saveAndFlush(new IdeaChatSession(user)).getId();
        primeAuth(user);

        mockMvc.perform(post(path(sessionId))
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ")))
                .andExpect(status().isBadRequest());

        assertThat(messageRepo.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId)).isEmpty();
    }

    private String path(Long sessionId) {
        return "/api/v1/chat-sessions/" + sessionId + "/messages";
    }

    private String body(String content) throws Exception {
        return mapper.writeValueAsString(Map.of("content", content));
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
        return UserFixture.createWithRole(userRepo, em, 1L);
    }
}
