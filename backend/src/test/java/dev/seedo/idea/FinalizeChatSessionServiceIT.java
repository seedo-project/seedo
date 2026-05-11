package dev.seedo.idea;

import dev.seedo.idea.application.ChatSessionAccessDeniedException;
import dev.seedo.idea.application.ChatSessionNotFinalizableException;
import dev.seedo.idea.application.ChatSessionNotFoundException;
import dev.seedo.idea.application.FinalizeChatSessionCommand;
import dev.seedo.idea.application.FinalizeChatSessionResult;
import dev.seedo.idea.application.FinalizeChatSessionService;
import dev.seedo.idea.domain.ChatSessionStatus;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaChatSession;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.domain.IdeaStatus;
import dev.seedo.idea.infrastructure.IdeaChatSessionRepository;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FinalizeChatSessionService} 의 트랜잭션 무결성 + 소유권 가드 + 동시성.
 *
 * <p>네 갱신이 한 트랜잭션에 묶여야 한다 (CLAUDE.md §8.4): ideas INSERT, idea_documents INSERT (v=1),
 * ideas.current_version_id UPDATE, idea_chat_sessions → FINALIZED. 도메인 메서드 + V2 의 CHECK 가
 * status ↔ idea_id ↔ finalized_at 정합성을 강제.
 *
 * <p>클래스 레벨 {@code @Transactional} 미사용 — 동시성 테스트에서 워커 스레드가 셋업 row 를 봐야 함.
 * 매 테스트는 새 UUID 사용자로 격리된다.
 */
class FinalizeChatSessionServiceIT extends AbstractIntegrationTest {

    @Autowired
    private FinalizeChatSessionService service;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository documentRepo;

    @Autowired
    private IdeaChatSessionRepository sessionRepo;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    @Test
    void happy_path_creates_draft_idea_with_v1_document_and_finalizes_session() {
        Fixture f = setupSession();

        FinalizeChatSessionResult result = service.finalize(
                new FinalizeChatSessionCommand(f.sessionId, f.user, "제목", "본문 마크다운"));

        Idea idea = ideaRepo.findById(result.ideaId()).orElseThrow();
        assertThat(idea.getStatus()).isEqualTo(IdeaStatus.DRAFT);
        assertThat(idea.getAuthorId()).isEqualTo(f.user);
        assertThat(idea.getCurrentVersionId()).isEqualTo(result.documentId());
        assertThat(idea.getPriceCredits()).isEqualTo(10);
        assertThat(idea.getRewardCredits()).isEqualTo(5);

        IdeaDocument doc = documentRepo.findById(result.documentId()).orElseThrow();
        assertThat(doc.getIdeaId()).isEqualTo(idea.getId());
        assertThat(doc.getVersion()).isEqualTo(1);
        assertThat(doc.getTitle()).isEqualTo("제목");
        assertThat(doc.getContentMd()).isEqualTo("본문 마크다운");

        IdeaChatSession reloaded = sessionRepo.findById(f.sessionId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ChatSessionStatus.FINALIZED);
        assertThat(reloaded.getIdeaId()).isEqualTo(idea.getId());
        assertThat(reloaded.getFinalizedAt()).isNotNull();
        assertThat(reloaded.getAbandonedAt()).isNull();
    }

    @Test
    void non_owner_is_denied() {
        UUID owner = tx.execute(s -> UserFixture.create(userRepo));
        UUID intruder = tx.execute(s -> UserFixture.create(userRepo));
        Long sessionId = tx.execute(s -> sessionRepo.saveAndFlush(new IdeaChatSession(owner)).getId());

        assertThatThrownBy(() -> service.finalize(
                new FinalizeChatSessionCommand(sessionId, intruder, "t", "c")))
                .isInstanceOf(ChatSessionAccessDeniedException.class);

        // 어떤 부수효과도 발생하지 않아야 한다 — 세션은 IN_PROGRESS, 이 세션 소유주의 idea 0 개.
        assertThat(sessionRepo.findById(sessionId).orElseThrow().getStatus())
                .isEqualTo(ChatSessionStatus.IN_PROGRESS);
        assertThat(countIdeasByAuthor(owner)).isZero();
        assertThat(countIdeasByAuthor(intruder)).isZero();
    }

    @Test
    void already_finalized_session_cannot_be_finalized_again() {
        Fixture f = setupSession();
        service.finalize(new FinalizeChatSessionCommand(f.sessionId, f.user, "t1", "c1"));

        assertThatThrownBy(() -> service.finalize(
                new FinalizeChatSessionCommand(f.sessionId, f.user, "t2", "c2")))
                .isInstanceOf(ChatSessionNotFinalizableException.class);
    }

    @Test
    void abandoned_session_cannot_be_finalized() {
        UUID user = tx.execute(s -> UserFixture.create(userRepo));
        Long sessionId = tx.execute(s -> {
            IdeaChatSession session = sessionRepo.saveAndFlush(new IdeaChatSession(user));
            session.abandon();
            sessionRepo.saveAndFlush(session);
            return session.getId();
        });

        assertThatThrownBy(() -> service.finalize(
                new FinalizeChatSessionCommand(sessionId, user, "t", "c")))
                .isInstanceOf(ChatSessionNotFinalizableException.class);
    }

    @Test
    void missing_session_throws_not_found() {
        UUID user = tx.execute(s -> UserFixture.create(userRepo));

        assertThatThrownBy(() -> service.finalize(
                new FinalizeChatSessionCommand(999_999L, user, "t", "c")))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    /**
     * 같은 세션을 두 스레드가 동시에 finalize 시도 — 세션 row 락(PESSIMISTIC_WRITE) 이 직렬화하므로
     * 정확히 한 건만 성공하고 패자는 락 풀린 뒤 IN_PROGRESS 검증에 걸린다. 부수효과:
     * idea / idea_documents 가 패자가 만들 row 없이 정확히 1 개씩만 남아야 한다.
     *
     * <p>락이 없으면 두 스레드가 모두 IN_PROGRESS 통과 → 2 개의 (idea, doc) 쌍 INSERT 후
     * 세션 UPDATE 만 last-writer-wins → 패자 idea/doc 가 고아로 남는 경로가 열린다. 이 케이스가
     * 그 회귀를 막는다.
     */
    @Test
    void concurrent_finalize_same_session_only_one_succeeds() throws Exception {
        Fixture f = setupSession();

        ExecutorService exec = Executors.newFixedThreadPool(2);
        int successCount;
        List<Throwable> errors;
        try {
            // ready latch 없이 start.countDown() 만 쓰면 한 스레드가 늦게 await() 진입한 경우
            // 사실상 순차 실행이 되어 락 회귀 (PESSIMISTIC_WRITE 제거) 를 못 잡는다. 두 워커가
            // 모두 start.await() 에 도착했음을 확인한 뒤 동시에 풀어 진짜 경쟁을 강제.
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<FinalizeChatSessionResult> a = exec.submit(() -> {
                ready.countDown();
                start.await();
                return service.finalize(new FinalizeChatSessionCommand(f.sessionId, f.user, "t1", "c1"));
            });
            Future<FinalizeChatSessionResult> b = exec.submit(() -> {
                ready.countDown();
                start.await();
                return service.finalize(new FinalizeChatSessionCommand(f.sessionId, f.user, "t2", "c2"));
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            errors = new ArrayList<>();
            int success = 0;
            for (Future<FinalizeChatSessionResult> fut : List.of(a, b)) {
                try {
                    fut.get(15, TimeUnit.SECONDS);
                    success++;
                } catch (Exception e) {
                    errors.add(e.getCause() != null ? e.getCause() : e);
                }
            }
            successCount = success;
        } finally {
            exec.shutdown();
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(errors).hasSize(1);
        // 패자는 락 풀린 뒤 FINALIZED 상태로 읽어 IN_PROGRESS 가드에 걸려야 한다.
        assertThat(errors.get(0)).isInstanceOf(ChatSessionNotFinalizableException.class);

        // 세션은 FINALIZED, idea_id 가 유일하게 살아남은 idea 와 연결.
        IdeaChatSession finalSession = sessionRepo.findById(f.sessionId).orElseThrow();
        assertThat(finalSession.getStatus()).isEqualTo(ChatSessionStatus.FINALIZED);
        assertThat(finalSession.getIdeaId()).isNotNull();

        // 가장 중요한 검증: (idea, doc) 가 패자 몫까지 두 쌍 만들어지지 않았다.
        assertThat(countIdeasByAuthor(f.user)).isEqualTo(1L);
        assertThat(countDocumentsByAuthor(f.user)).isEqualTo(1L);
    }

    private record Fixture(UUID user, Long sessionId) {
    }

    private Fixture setupSession() {
        return tx.execute(status -> {
            UUID user = UserFixture.create(userRepo);
            Long sessionId = sessionRepo.saveAndFlush(new IdeaChatSession(user)).getId();
            return new Fixture(user, sessionId);
        });
    }

    private long countIdeasByAuthor(UUID author) {
        return ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM ideas WHERE author_id = CAST(:a AS uuid)")
                .setParameter("a", author.toString())
                .getSingleResult()).longValue();
    }

    private long countDocumentsByAuthor(UUID author) {
        return ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM idea_documents d "
                                + "JOIN ideas i ON i.id = d.idea_id "
                                + "WHERE i.author_id = CAST(:a AS uuid)")
                .setParameter("a", author.toString())
                .getSingleResult()).longValue();
    }
}
