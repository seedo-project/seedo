package dev.seedo.idea;

import dev.seedo.credit.domain.UserCredit;
import dev.seedo.credit.infrastructure.UserCreditRepository;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.user.domain.User;
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
 * HTTP 레이어 통합 — {@link dev.seedo.common.web.ApiResponse} 봉투, {@link dev.seedo.common.web.CurrentUserId}
 * 어노테이션, {@code /api/v1} prefix, {@link IdeaExceptionHandler} 가 모두 한 줄로 흐르는지 검증한다.
 *
 * <p>JwtDecoder 만 모킹 — JWKS 호출은 우회하고, 그 외 필터/컨버터/메서드 보안/advice 는 모두 진짜.
 */
@AutoConfigureMockMvc
@Transactional
class IdeaPurchaseControllerIT extends AbstractIntegrationTest {

    private static final String BEARER = "Bearer test-token";

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

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
    void successful_purchase_returns_ok_envelope_with_data() throws Exception {
        UUID author = createUser();
        UUID buyer = createUserWithUserRoleAndCredit(50L);
        Long ideaId = setupPublishedIdea(author, 10);
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(buyer));

        mockMvc.perform(post("/api/v1/ideas/" + ideaId + "/purchase").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data.balance").value(40))
                .andExpect(jsonPath("$.data.purchaseId").isNumber())
                .andExpect(jsonPath("$.data.documentId").isNumber());
    }

    @Test
    void duplicate_purchase_returns_error_envelope_with_409() throws Exception {
        UUID author = createUser();
        UUID buyer = createUserWithUserRoleAndCredit(50L);
        Long ideaId = setupPublishedIdea(author, 10);
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(buyer));

        mockMvc.perform(post("/api/v1/ideas/" + ideaId + "/purchase").header("Authorization", BEARER))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ideas/" + ideaId + "/purchase").header("Authorization", BEARER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("already purchased")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void old_path_without_api_v1_returns_404() throws Exception {
        UUID buyer = createUserWithUserRoleAndCredit(50L);
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(buyer));

        // /api/v1 prefix 가 자동 부착되므로 옛 경로는 매핑되지 않는다.
        mockMvc.perform(post("/ideas/1/purchase").header("Authorization", BEARER))
                .andExpect(status().isNotFound());
    }

    private Jwt jwtFor(UUID sub) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(sub.toString())
                .claim("iss", "https://test/auth/v1")
                .build();
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        userRepo.saveAndFlush(new User(id, "u-" + id + "@test", "n-" + id.toString().substring(0, 8)));
        return id;
    }

    private UUID createUserWithUserRoleAndCredit(long initialBalance) {
        UUID id = createUser();
        em.createNativeQuery(
                        "INSERT INTO user_roles(user_id, role_id) VALUES (CAST(:uid AS uuid), 1)")
                .setParameter("uid", id.toString())
                .executeUpdate();
        UserCredit credit = new UserCredit(id);
        if (initialBalance > 0) {
            credit.applyDelta(initialBalance);
        }
        creditRepo.saveAndFlush(credit);
        em.flush();
        return id;
    }

    private Long setupPublishedIdea(UUID author, int price) {
        Idea idea = ideaRepo.saveAndFlush(new Idea(author, price, 5));
        IdeaDocument doc = docRepo.saveAndFlush(new IdeaDocument(idea.getId(), 1, "t", "c"));
        idea.updateCurrentVersion(doc.getId());
        idea.publish();
        return ideaRepo.saveAndFlush(idea).getId();
    }
}
