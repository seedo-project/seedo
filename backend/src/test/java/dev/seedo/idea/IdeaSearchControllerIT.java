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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 하이브리드 검색 API 의 HTTP 레이어 통합 — 임베딩/키워드 양쪽 신호의 RRF 결합, PUBLISHED 가드,
 * limit 클램프, 빈 쿼리 400. RRF / fallback 의 세부 분기는 {@link SearchIdeasServiceIT} 가 cover.
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
    void returns_top_match_first_with_keywords() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID viewer = UserFixture.create(userRepo);
        // ideaB 는 임베딩 cosine 1 + 키워드 overlap("스터디") — 두 신호 모두에서 1위.
        Long ideaA = publishedWithEmbedding(author, oneHot(0), List.of("학습", "타이머"));
        Long ideaB = publishedWithEmbedding(author, oneHot(1), List.of("스터디", "그룹"));
        Long ideaC = publishedWithEmbedding(author, oneHot(2), List.of("운동"));

        primeAuth(viewer);
        when(embeddingClient.embed("스터디 메이트")).thenReturn(oneHot(1));

        mockMvc.perform(get(PATH).param("q", "스터디 메이트").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].ideaId").value(ideaB))
                // RRF 점수의 절대값은 해석하지 않는다 — 단지 양수이고, ideaA/C 보다 더 크다는 사실만 검증.
                .andExpect(jsonPath("$.data[0].score").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.data[0].priceCredits").value(10))
                // 카드 노출용 키워드가 응답에 그대로 포함되어야 한다 (페이지 구조 S201).
                .andExpect(jsonPath("$.data[0].keywords[0]").value("스터디"))
                .andExpect(jsonPath("$.data[0].keywords[1]").value("그룹"))
                // ideaA / ideaC 는 키워드 overlap 0 + cosine 0 이지만 임베딩 결과의 candidate 풀에 들어와 있으므로 노출.
                .andExpect(jsonPath("$.data[?(@.ideaId == " + ideaA + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.ideaId == " + ideaC + ")]").exists());
    }

    @Test
    void excludes_draft_and_archived() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID viewer = UserFixture.create(userRepo);

        Long publishedId = publishedWithEmbedding(author, oneHot(0), List.of());
        // DRAFT 에도 임베딩 박아 — 노출되면 안 된다.
        Idea draft = IdeaFixture.createDraft(ideaRepo, author, 10, 5);
        embeddingRepo.upsertEmbedding(draft.getId(), oneHot(0), List.of());

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

        Long withEmbedding = publishedWithEmbedding(author, oneHot(0), List.of());
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
            publishedWithEmbedding(author, oneHot(i), List.of());
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

    /**
     * 키워드 매칭만 있고 임베딩 신호는 어느 쪽도 닿지 않는 시나리오 — 쿼리 토큰이 정확히 ideaA 의 keywords
     * 와 겹치고, 임베딩은 ideaC 와 동일하지만 키워드 신호의 RRF 가중치가 더 커서 ideaA 가 위로 와야 한다.
     */
    @Test
    void keyword_match_outranks_embedding_only_match() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID viewer = UserFixture.create(userRepo);
        // ideaA: 키워드 매칭 1개 ("타이머"), 임베딩 cosine 0.
        Long ideaA = publishedWithEmbedding(author, oneHot(0), List.of("학습", "타이머"));
        // ideaC: 키워드 매칭 0, 임베딩 cosine 1 — 임베딩 단독으로는 1위지만 키워드 신호가 없어 RRF 에선 ideaA 보다 같은 가중치.
        Long ideaC = publishedWithEmbedding(author, oneHot(2), List.of("운동"));

        primeAuth(viewer);
        when(embeddingClient.embed("타이머")).thenReturn(oneHot(2));

        mockMvc.perform(get(PATH).param("q", "타이머").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                // 두 신호 모두에서 rank 1 인 ideaA 가 위 (키워드 1위 + 임베딩 2위) > ideaC (임베딩 1위 only).
                .andExpect(jsonPath("$.data[0].ideaId").value(ideaA))
                .andExpect(jsonPath("$.data[1].ideaId").value(ideaC));
    }

    /**
     * 임베딩 단독 매칭 — 쿼리 토큰이 어떤 카드의 keywords 와도 겹치지 않는 시나리오. 키워드 결과 빈
     * 리스트라 임베딩 결과만으로 정렬된다 (cosine 거리 오름차순 = RRF rank 1, 2, 3 순).
     */
    @Test
    void embedding_only_orders_by_cosine() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID viewer = UserFixture.create(userRepo);
        Long ideaA = publishedWithEmbedding(author, oneHot(0), List.of("학습"));
        Long ideaB = publishedWithEmbedding(author, oneHot(1), List.of("운동"));

        primeAuth(viewer);
        // 쿼리 "abcxyz" 는 어떤 keywords 와도 겹치지 않음. 임베딩만 ideaB 와 동일 (1-hot dim=1).
        when(embeddingClient.embed("abcxyz")).thenReturn(oneHot(1));

        mockMvc.perform(get(PATH).param("q", "abcxyz").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].ideaId").value(ideaB))
                .andExpect(jsonPath("$.data[1].ideaId").value(ideaA));
    }

    /**
     * graceful degradation — 임베딩 클라이언트가 던지면 키워드 결과만으로 응답이 살아남는다.
     * 임베딩이 빠지면 OpenAI 의 외부 의존이 죽어도 검색 자체는 동작 (이슈 #138).
     */
    @Test
    void embedding_failure_falls_back_to_keyword_only() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID viewer = UserFixture.create(userRepo);
        Long ideaA = publishedWithEmbedding(author, oneHot(0), List.of("스터디", "타이머"));
        Long ideaB = publishedWithEmbedding(author, oneHot(1), List.of("운동"));

        primeAuth(viewer);
        when(embeddingClient.embed(anyString()))
                .thenThrow(new RuntimeException("openai 5xx (graceful degradation 검증용)"));

        mockMvc.perform(get(PATH).param("q", "스터디").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].ideaId").value(ideaA))
                .andExpect(jsonPath("$.data[?(@.ideaId == " + ideaB + ")]").doesNotExist());
    }

    /**
     * 토큰 분리 / 정규화 — 쿼리에 구두점·공백이 섞여도 lower-case 토큰으로 분리해 keywords 와 매칭된다.
     */
    @Test
    void tokenizer_splits_on_punctuation_and_lowercases() throws Exception {
        UUID author = UserFixture.create(userRepo);
        UUID viewer = UserFixture.create(userRepo);
        Long ideaA = publishedWithEmbedding(author, oneHot(0), List.of("ios", "앱"));

        primeAuth(viewer);
        when(embeddingClient.embed(any())).thenReturn(oneHot(5));

        mockMvc.perform(get(PATH).param("q", "iOS, 앱!").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ideaId").value(ideaA))
                // 카드 키워드는 finalize 시 저장된 그대로 (lower-case 가정).
                .andExpect(jsonPath("$.data[0].keywords[0]").value("ios"));
    }

    private Long publishedWithEmbedding(UUID author, float[] embedding, List<String> keywords) {
        Long ideaId = IdeaFixture.createPublished(ideaRepo, docRepo, author, 10, 5).getId();
        embeddingRepo.upsertEmbedding(ideaId, embedding, keywords);
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
