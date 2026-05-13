package dev.seedo.idea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.seedo.idea.domain.ChatMessageRole;
import dev.seedo.idea.domain.IdeaChatMessage;
import dev.seedo.idea.domain.IdeaChatSession;
import dev.seedo.idea.infrastructure.IdeaChatMessageRepository;
import dev.seedo.idea.infrastructure.IdeaChatSessionRepository;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.IntegrationTestStubsConfig;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/chat-sessions/{id}/finalize} HTTP 통합 — JWT 인증 → @PreAuthorize → service →
 * ApiResponse 봉투까지 한 줄로 흐르는지 검증. 본문 자동 작성 흐름 (#127): 클라가 마크다운을 보내지 않고
 * LLM 응답이 그대로 저장된다. {@link dev.seedo.idea.application.port.out.ChatClient} 는 stub 빈
 * (`stub title` + 5개 섹션 STUB_CONTENT_MD + 키워드 3개 결정적 응답).
 *
 * <p>클래스 레벨 {@code @Transactional} 을 쓰지 않는다 (lessons F.5). service 가 외부 호출 + INSERT 트랜잭션을
 * {@code TransactionTemplate} 으로 분리하므로 outer 와 join 되면 그 경계 자체가 검증 안 된다. 격리는 매 테스트가
 * 새 UUID 의 사용자를 만드는 row-level 격리로 충분.
 *
 * <p>{@code UserFixture.createWithRole} 안 native query 가 트랜잭션을 요구해 {@link TransactionTemplate#execute}
 * 안에서 호출한다 (PR #126 fix 경험).
 */
@AutoConfigureMockMvc
class ChatSessionFinalizeControllerIT extends AbstractIntegrationTest {

    private static final String PATH_TEMPLATE = "/api/v1/chat-sessions/%d/finalize";
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

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    @Test
    void successful_finalize_persists_llm_draft_as_idea_document() throws Exception {
        UUID user = createUserWithRole();
        Long sessionId = setupSessionWithMessage(user, "공부 습관 잡아주는 앱 만들고 싶어요");
        primeAuth(user);

        String responseBody = mockMvc.perform(post(path(sessionId)).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.ideaId").isNumber())
                .andExpect(jsonPath("$.data.documentId").isNumber())
                .andExpect(jsonPath("$.data.version").value(1))
                .andReturn().getResponse().getContentAsString();

        // LLM stub 응답이 그대로 idea_documents 에 저장됐는지 후속 DB 검증.
        JsonNode data = mapper.readTree(responseBody).path("data");
        Long ideaId = data.path("ideaId").asLong();
        Long docId = data.path("documentId").asLong();
        assertThat(ideaRepo.findById(ideaId).orElseThrow().getCurrentVersionId()).isEqualTo(docId);
        var doc = docRepo.findById(docId).orElseThrow();
        assertThat(doc.getTitle()).isEqualTo("stub title");
        // 5개 섹션 (## Problem ... ## Insight) 마크다운 — 페이지 구조 S303 정형화 회귀 가드.
        assertThat(doc.getContentMd()).isEqualTo(IntegrationTestStubsConfig.STUB_CONTENT_MD);
    }

    @Test
    void empty_chat_history_returns_400() throws Exception {
        UUID user = createUserWithRole();
        Long sessionId = tx.execute(s -> sessionRepo.saveAndFlush(new IdeaChatSession(user)).getId());
        primeAuth(user);

        mockMvc.perform(post(path(sessionId)).header("Authorization", BEARER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("no messages")));
    }

    @Test
    void non_owner_returns_403() throws Exception {
        UUID owner = UserFixture.create(userRepo);
        UUID intruder = createUserWithRole();
        Long sessionId = setupSessionWithMessage(owner, "hi");
        primeAuth(intruder);

        mockMvc.perform(post(path(sessionId)).header("Authorization", BEARER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("not owned by caller")));
    }

    @Test
    void missing_session_returns_404() throws Exception {
        UUID user = createUserWithRole();
        primeAuth(user);

        mockMvc.perform(post(path(999_999L)).header("Authorization", BEARER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("chat session not found")));
    }

    @Test
    void missing_authorization_returns_401() throws Exception {
        mockMvc.perform(post(path(1L)))
                .andExpect(status().isUnauthorized());
    }

    private Long setupSessionWithMessage(UUID userId, String text) {
        return tx.execute(s -> {
            IdeaChatSession session = sessionRepo.saveAndFlush(new IdeaChatSession(userId));
            messageRepo.saveAndFlush(new IdeaChatMessage(session.getId(), ChatMessageRole.USER, text));
            return session.getId();
        });
    }

    private String path(Long sessionId) {
        return String.format(PATH_TEMPLATE, sessionId);
    }

    private void primeAuth(UUID userId) {
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(userId));
    }

    private Jwt jwtFor(UUID sub) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(sub.toString())
                .claim("iss", "https://test/auth/v1")
                .build();
    }

    private UUID createUserWithRole() {
        // UserFixture.createWithRole 내부 em.createNativeQuery().executeUpdate() 가 트랜잭션 요구.
        return tx.execute(s -> UserFixture.createWithRole(userRepo, em, 1L));
    }
}
