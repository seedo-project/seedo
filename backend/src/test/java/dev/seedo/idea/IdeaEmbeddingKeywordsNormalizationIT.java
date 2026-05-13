package dev.seedo.idea;

import dev.seedo.idea.infrastructure.IdeaEmbeddingRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.IdeaFixture;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code idea_embeddings.keywords} 저장 정규화 검증 (#147). Repository 의 단일 게이트에서 lower-case +
 * trim + 중복 제거 + 빈 문자열 제외가 일관되게 적용되는지 확인 — 검색 측 {@code SearchIdeasService#tokenize}
 * 가 만든 토큰과 정확히 매칭되어야 하이브리드 검색이 의도대로 동작한다.
 */
@Transactional
class IdeaEmbeddingKeywordsNormalizationIT extends AbstractIntegrationTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaEmbeddingRepository embeddingRepo;

    @Test
    void mixed_case_keywords_stored_lowercase() {
        Long ideaId = newIdea();

        embeddingRepo.upsertEmbedding(ideaId, oneHot(0), List.of("iOS", "Android", "타이머"));

        assertThat(keywordsOf(ideaId)).containsExactly("ios", "android", "타이머");
    }

    @Test
    void trim_and_drop_blank_keywords() {
        Long ideaId = newIdea();

        embeddingRepo.upsertEmbedding(ideaId, oneHot(0), List.of("  공부  ", "", "  ", "타이머"));

        // 공백 제거 후 빈 문자열은 탈락.
        assertThat(keywordsOf(ideaId)).containsExactly("공부", "타이머");
    }

    @Test
    void dedupe_after_normalization() {
        Long ideaId = newIdea();

        // 대소문자 + 공백 차이로 입력에선 distinct 처럼 보이지만 정규화하면 동일.
        embeddingRepo.upsertEmbedding(ideaId, oneHot(0), List.of("iOS", "ios", "  IOS  ", "안드로이드"));

        assertThat(keywordsOf(ideaId)).containsExactly("ios", "안드로이드");
    }

    @Test
    void all_blank_input_keeps_previous_keywords() {
        Long ideaId = newIdea();
        // 첫 upsert — 정규화된 키워드 저장.
        embeddingRepo.upsertEmbedding(ideaId, oneHot(0), List.of("학습"));

        // 두 번째 upsert — 빈 문자열만 들어와 정규화 후 빈 List → keywords 갱신 skip (이전 보존).
        embeddingRepo.upsertEmbedding(ideaId, oneHot(1), List.of("", "  "));

        assertThat(keywordsOf(ideaId)).containsExactly("학습");
    }

    private Long newIdea() {
        UUID author = UserFixture.create(userRepo);
        return IdeaFixture.createDraft(ideaRepo, author, 10, 5).getId();
    }

    private static float[] oneHot(int dim) {
        float[] v = new float[1536];
        v[dim] = 1.0f;
        return v;
    }

    @SuppressWarnings("unchecked")
    private List<String> keywordsOf(Long ideaId) {
        Object value = em.createNativeQuery(
                        "SELECT keywords FROM idea_embeddings WHERE idea_id = :id")
                .setParameter("id", ideaId)
                .getSingleResult();
        if (value instanceof String[] arr) {
            return List.of(arr);
        }
        if (value instanceof java.sql.Array sqlArray) {
            try {
                Object inner = sqlArray.getArray();
                if (inner instanceof String[] arr) {
                    return List.of(arr);
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("failed to read keywords array", e);
            }
        }
        throw new IllegalStateException("unexpected keywords column type");
    }
}
