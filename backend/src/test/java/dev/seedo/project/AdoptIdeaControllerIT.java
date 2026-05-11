package dev.seedo.project;

import dev.seedo.credit.infrastructure.UserCreditRepository;
import dev.seedo.idea.application.PurchaseIdeaService;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/ideas/{id}/adopt} HTTP 통합 — JWT → @PreAuthorize (PERM_PROJECT_CREATE) → service →
 * ApiResponse 봉투. ProjectExceptionHandler 가 4xx 를 ERROR 봉투로 매핑하는지 검증.
 */
@AutoConfigureMockMvc
@Transactional
class AdoptIdeaControllerIT extends AbstractIntegrationTest {

    private static final String BEARER = "Bearer test-token";
    private static final int PRICE = 10;
    private static final int REWARD = 5;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PurchaseIdeaService purchaseService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private UserCreditRepository creditRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository docRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void external_adopter_returns_ok_envelope_with_reward_paid() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UserFixture.grantCredit(creditRepo, author, 0L);
        UUID buyer = createUserWithRole();
        UserFixture.grantCredit(creditRepo, buyer, PRICE);
        Long ideaId = IdeaFixture.createPublished(ideaRepo, docRepo, author, PRICE, REWARD).getId();
        purchaseService.purchase(ideaId, buyer);
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(buyer));

        mockMvc.perform(post("/api/v1/ideas/" + ideaId + "/adopt").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data.projectId").isNumber())
                .andExpect(jsonPath("$.data.rewardPaid").value(true))
                .andExpect(jsonPath("$.data.rewardTransactionId").isNumber());
    }

    @Test
    void self_adoption_returns_reward_paid_false() throws Exception {
        UUID author = createUserWithRole();
        UserFixture.grantCredit(creditRepo, author, 0L);
        Long ideaId = IdeaFixture.createPublished(ideaRepo, docRepo, author, PRICE, REWARD).getId();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(author));

        mockMvc.perform(post("/api/v1/ideas/" + ideaId + "/adopt").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardPaid").value(false))
                // @JsonInclude(NON_NULL) 로 null 은 응답에서 사라진다
                .andExpect(jsonPath("$.data.rewardTransactionId").doesNotExist());
    }

    @Test
    void non_purchaser_returns_400_envelope() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID stranger = createUserWithRole();
        Long ideaId = IdeaFixture.createPublished(ideaRepo, docRepo, author, PRICE, REWARD).getId();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(stranger));

        mockMvc.perform(post("/api/v1/ideas/" + ideaId + "/adopt").header("Authorization", BEARER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("requires prior purchase")));
    }

    @Test
    void draft_idea_returns_400_envelope() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID adopter = createUserWithRole();
        Long ideaId = IdeaFixture.createDraft(ideaRepo, author, PRICE, REWARD).getId();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(adopter));

        mockMvc.perform(post("/api/v1/ideas/" + ideaId + "/adopt").header("Authorization", BEARER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not adoptable")));
    }

    @Test
    void missing_idea_returns_404_envelope() throws Exception {
        UUID adopter = createUserWithRole();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(adopter));

        mockMvc.perform(post("/api/v1/ideas/999999/adopt").header("Authorization", BEARER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("idea not found")));
    }

    @Test
    void missing_authorization_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/ideas/1/adopt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void user_without_role_returns_403() throws Exception {
        UUID user = UserFixture.create(userRepo); // role 부여 안 함 → PERM_PROJECT_CREATE 없음
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(user));

        mockMvc.perform(post("/api/v1/ideas/1/adopt").header("Authorization", BEARER))
                .andExpect(status().isForbidden());
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
