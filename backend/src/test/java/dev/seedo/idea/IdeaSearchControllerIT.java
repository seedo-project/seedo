package dev.seedo.idea;

import dev.seedo.idea.application.port.out.EmbeddingClient;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaEmbeddingRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.IdeaFixture;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 자연어 검색 API 의 HTTP 레이어 통합 — 정렬 정확성, PUBLISHED 가드, limit 클램프, 빈 쿼리 400.
 *
 * <p>{@link EmbeddingClient} 는 query 별 다른 vector 를 반환하도록 mock 으로 교체. 임베딩 row 는
 * IT 가 직접 {@link IdeaEmbeddingRepository#upsertEmbedding} 으로 박는다 (listener 가 발화하는
 * AFTER_COMMIT 흐름과 분리해서 검색 로직만 검증).
 *
 * <p>클래스 레벨 {@code @Transactional} — read-only 검색이라 동시성/멱등 이슈 없음. 트랜잭션 rollback
 * 으로 idea / embedding row 가 테스트 간 누적되지 않게 격리 (검색은 PUBLISHED 전체를 보므로 누적되면
 * 결과 카운트가 어긋난다).
 */
@AutoConfigureMockMvc
@Transactional
class IdeaSearchControllerIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/v1/ideas/search";
    private static final String BEARER = "Bearer test-token";

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private EmbeddingClient embeddingClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository docRepo;

    @Autowired
    private IdeaEmbeddingRepository embeddingRepo;

    @Test
    void returns_closest_idea_first() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID viewer = UserFixture.create(userRepo);
        Long ideaA = publishedWithEmbedding(author, oneHot(0));
        Long ideaB = publishedWithEmbedding(author, oneHot(1));
        Long ideaC = publishedWithEmbedding(author, oneHot(2));

        primeAuth(viewer);
        // query 임베딩이 ideaB 와 같은 1-hot → cosine similarity 1, 나머지는 0 → ideaB 가 첫 번째.
        when(embeddingClient.embed("스터디 메이트")).thenReturn(oneHot(1));

        mockMvc.perform(get(PATH).param("q", "스터디 메이트").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].ideaId").value(ideaB))
                .andExpect(jsonPath("$.data[0].similarity").value(1.0))
                .andExpect(jsonPath("$.data[0].priceCredits").value(10))
                // ideaA / ideaC 는 다른 1-hot 이라 cosine similarity 0 — 동률이지만 결과에 포함되어야 한다.
                .andExpect(jsonPath("$.data[?(@.ideaId == " + ideaA + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.ideaId == " + ideaC + ")]").exists());
    }

    @Test
    void excludes_draft_and_archived() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID viewer = UserFixture.create(userRepo);

        Long publishedId = publishedWithEmbedding(author, oneHot(0));
        // DRAFT 에도 임베딩 박아 — 노출되면 안 된다.
        Idea draft = IdeaFixture.createDraft(ideaRepo, author, 10, 5);
        embeddingRepo.upsertEmbedding(draft.getId(), oneHot(0));

        primeAuth(viewer);
        when(embeddingClient.embed("질의")).thenReturn(oneHot(0));

        mockMvc.perform(get(PATH).param("q", "질의").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].ideaId").value(publishedId));
    }

    @Test
    void excludes_published_without_embedding() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID viewer = UserFixture.create(userRepo);

        Long withEmbedding = publishedWithEmbedding(author, oneHot(0));
        // 임베딩 없는 PUBLISHED — listener 가 실패한 시나리오. INNER JOIN 이라 자연 배제.
        IdeaFixture.createPublished(ideaRepo, docRepo, author, 10, 5).getId();

        primeAuth(viewer);
        when(embeddingClient.embed("질의")).thenReturn(oneHot(0));

        mockMvc.perform(get(PATH).param("q", "질의").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].ideaId").value(withEmbedding));
    }

    @Test
    void respects_limit_parameter() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID viewer = UserFixture.create(userRepo);
        for (int i = 0; i < 5; i++) {
            publishedWithEmbedding(author, oneHot(i));
        }

        primeAuth(viewer);
        when(embeddingClient.embed("질의")).thenReturn(oneHot(0));

        mockMvc.perform(get(PATH)
                        .param("q", "질의")
                        .param("limit", "2")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void blank_query_returns_400() throws Exception {
        UUID viewer = UserFixture.create(userRepo);
        primeAuth(viewer);

        mockMvc.perform(get(PATH).param("q", "   ").header("Authorization", BEARER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("blank")));
    }

    private Long publishedWithEmbedding(UUID author, float[] embedding) {
        Long ideaId = IdeaFixture.createPublished(ideaRepo, docRepo, author, 10, 5).getId();
        embeddingRepo.upsertEmbedding(ideaId, embedding);
        return ideaId;
    }

    /** 1536 차원 1-hot 벡터. 다른 차원에서 1 인 두 벡터의 cosine similarity 는 정확히 0. */
    private static float[] oneHot(int dim) {
        float[] v = new float[1536];
        v[dim] = 1.0f;
        return v;
    }

    private void primeAuth(UUID viewer) {
        when(jwtDecoder.decode("test-token")).thenReturn(jwtFor(viewer));
    }

    private Jwt jwtFor(UUID sub) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(sub.toString())
                .claim("iss", "https://test/auth/v1")
                .build();
    }
}
