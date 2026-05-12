package dev.seedo.idea;

import dev.seedo.idea.application.FinalizeChatSessionCommand;
import dev.seedo.idea.application.FinalizeChatSessionService;
import dev.seedo.idea.application.PublishIdeaVersionCommand;
import dev.seedo.idea.application.PublishIdeaVersionService;
import dev.seedo.idea.application.port.out.EmbeddingClient;
import dev.seedo.idea.domain.ChatMessageRole;
import dev.seedo.idea.domain.IdeaChatMessage;
import dev.seedo.idea.domain.IdeaChatSession;
import dev.seedo.idea.infrastructure.IdeaChatMessageRepository;
import dev.seedo.idea.infrastructure.IdeaChatSessionRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link dev.seedo.idea.application.IdeaEmbeddingRefreshListener} 의 동작 검증.
 *
 * <p>finalize / publish 트랜잭션이 commit 된 뒤 AFTER_COMMIT 단계에서 {@link EmbeddingClient} 가
 * 호출되고 {@code idea_embeddings} 에 row 가 upsert 되는지 확인. 실제 OpenAI 호출 안 함 —
 * {@code @MockitoBean} 으로 어댑터 자리에 mock 주입.
 *
 * <p>@Transactional 클래스 레벨 미사용 — AFTER_COMMIT 이벤트가 발화하려면 본 트랜잭션이 commit 되어야 한다.
 * {@link TransactionTemplate} 로 명시 commit.
 */
class IdeaEmbeddingRefreshListenerIT extends AbstractIntegrationTest {

    @MockitoBean
    private EmbeddingClient embeddingClient;

    @Autowired
    private FinalizeChatSessionService finalizeService;

    @Autowired
    private PublishIdeaVersionService publishService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaChatSessionRepository sessionRepo;

    @Autowired
    private IdeaChatMessageRepository messageRepo;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    /**
     * 차원 0 만 1.0 으로 표시 — 호출 결과가 정말 stub 인지 native query 결과로 확인 가능.
     * pgvector(1536) 컬럼 정의와 차원을 맞춰야 INSERT 가 통과한다.
     */
    private static float[] stubVector() {
        float[] vec = new float[1536];
        vec[0] = 1.0f;
        return vec;
    }

    @BeforeEach
    void primeStub() {
        when(embeddingClient.embed(anyString())).thenReturn(stubVector());
    }

    @Test
    void finalize_triggers_embedding_upsert_after_commit() {
        UUID user = tx.execute(s -> UserFixture.create(userRepo));
        Long sessionId = setupSessionWithMessage(user, "대화 한 줄");

        Long ideaId = finalizeService.finalize(
                new FinalizeChatSessionCommand(sessionId, user)).ideaId();

        // AFTER_COMMIT 리스너는 finalize 트랜잭션 commit 후 같은 스레드에서 동기 실행되지만,
        // 향후 비동기 분기 도입 대비 Awaitility 로 폴링.
        await().untilAsserted(() -> {
            assertThat(embeddingRowExists(ideaId)).isTrue();
            assertThat(firstDimension(ideaId)).isEqualTo(1.0f);
        });
        // finalize 본문은 ChatClient stub draft → "stub content markdown" 가 임베딩에 보내짐.
        verify(embeddingClient, atLeastOnce()).embed("stub content markdown");
    }

    @Test
    void publish_triggers_embedding_upsert_overwriting_previous() {
        UUID user = tx.execute(s -> UserFixture.create(userRepo));
        Long sessionId = setupSessionWithMessage(user, "초기 대화");

        Long ideaId = finalizeService.finalize(
                new FinalizeChatSessionCommand(sessionId, user)).ideaId();
        await().untilAsserted(() -> assertThat(embeddingRowExists(ideaId)).isTrue());

        // publish 는 클라가 보낸 새 버전 본문을 그대로 사용 (finalize 와 달리 LLM 합성 없음).
        publishService.publish(new PublishIdeaVersionCommand(ideaId, user, "v2", "본문 v2"));

        // upsert 동작 — 같은 idea_id 가 ON CONFLICT 로 UPDATE. 한 row 만 남는다.
        await().untilAsserted(() -> {
            assertThat(embeddingRowCount(ideaId)).isEqualTo(1L);
            assertThat(firstDimension(ideaId)).isEqualTo(1.0f);
        });
        verify(embeddingClient).embed("stub content markdown");
        verify(embeddingClient).embed("본문 v2");
    }

    @Test
    void embedding_client_failure_does_not_break_user_flow() {
        UUID user = tx.execute(s -> UserFixture.create(userRepo));
        Long sessionId = setupSessionWithMessage(user, "한 줄");

        doThrow(new RuntimeException("simulated OpenAI 503"))
                .when(embeddingClient).embed(anyString());

        // 사용자 흐름 (finalize) 은 정상 commit, 예외 전파 안 됨
        Long ideaId = finalizeService.finalize(
                new FinalizeChatSessionCommand(sessionId, user)).ideaId();
        assertThat(ideaId).isPositive();

        // 임베딩 row 는 생성되지 않음 (listener 가 swallow)
        assertThat(embeddingRowExists(ideaId)).isFalse();
    }

    /**
     * #127 로 finalize 가 빈 chat history 를 거부하므로 모든 IT 셋업이 메시지 1개를 미리 INSERT 한다.
     */
    private Long setupSessionWithMessage(UUID user, String message) {
        return tx.execute(s -> {
            IdeaChatSession session = sessionRepo.saveAndFlush(new IdeaChatSession(user));
            messageRepo.saveAndFlush(new IdeaChatMessage(session.getId(), ChatMessageRole.USER, message));
            return session.getId();
        });
    }

    private boolean embeddingRowExists(Long ideaId) {
        Number count = (Number) em.createNativeQuery(
                        "SELECT count(*) FROM idea_embeddings WHERE idea_id = :id")
                .setParameter("id", ideaId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private long embeddingRowCount(Long ideaId) {
        Number count = (Number) em.createNativeQuery(
                        "SELECT count(*) FROM idea_embeddings WHERE idea_id = :id")
                .setParameter("id", ideaId)
                .getSingleResult();
        return count.longValue();
    }

    /**
     * pgvector 의 첫 차원 추출 — {@code (embedding::real[])[1]} 로 배열 캐스팅 후 1-based 인덱싱.
     * 1.0 이 나오면 stub 의 vec[0] = 1.0 이 그대로 저장된 것.
     */
    private float firstDimension(Long ideaId) {
        Number d = (Number) em.createNativeQuery(
                        "SELECT (embedding::real[])[1] FROM idea_embeddings WHERE idea_id = :id")
                .setParameter("id", ideaId)
                .getSingleResult();
        return d.floatValue();
    }
}
