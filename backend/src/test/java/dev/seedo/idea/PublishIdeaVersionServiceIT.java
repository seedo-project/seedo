package dev.seedo.idea;

import dev.seedo.idea.application.IdeaAccessDeniedException;
import dev.seedo.idea.application.IdeaNotFoundException;
import dev.seedo.idea.application.IdeaNotVersionableException;
import dev.seedo.idea.application.PublishIdeaVersionCommand;
import dev.seedo.idea.application.PublishIdeaVersionResult;
import dev.seedo.idea.application.PublishIdeaVersionService;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.IdeaFixture;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PublishIdeaVersionService} 의 트랜잭션 무결성 + 소유권 / 상태 가드 + 동시성.
 *
 * <p>버전 단조성: MAX(version)+1 로 새 row 가 쌓이고 {@code current_version_id} 는 항상 최신 row 를 가리킨다.
 * UNIQUE(idea_id, version) 는 DB 최후 방어선 — 정상 호출 흐름에서는 service 의 SELECT FOR UPDATE 직렬화가 잡는다.
 *
 * <p>클래스 레벨 {@code @Transactional} 미사용 — 동시성 테스트에서 워커 스레드가 셋업 row 를 봐야 함.
 */
class PublishIdeaVersionServiceIT extends AbstractIntegrationTest {

    @Autowired
    private PublishIdeaVersionService service;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository documentRepo;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void publish_v2_increments_version_and_moves_current_pointer() {
        Fixture f = setupPublishedIdea();
        Long v1DocId = ideaRepo.findById(f.ideaId).orElseThrow().getCurrentVersionId();

        PublishIdeaVersionResult result = service.publish(
                new PublishIdeaVersionCommand(f.ideaId, f.author, "t2", "c2"));

        assertThat(result.version()).isEqualTo(2);
        assertThat(result.documentId()).isNotEqualTo(v1DocId);

        Idea reloaded = ideaRepo.findById(f.ideaId).orElseThrow();
        assertThat(reloaded.getCurrentVersionId()).isEqualTo(result.documentId());

        IdeaDocument v2 = documentRepo.findById(result.documentId()).orElseThrow();
        assertThat(v2.getIdeaId()).isEqualTo(f.ideaId);
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.getTitle()).isEqualTo("t2");
        assertThat(v2.getContentMd()).isEqualTo("c2");
    }

    @Test
    void successive_publishes_produce_monotonic_versions() {
        Fixture f = setupPublishedIdea();

        int v2 = service.publish(new PublishIdeaVersionCommand(f.ideaId, f.author, "t2", "c2")).version();
        int v3 = service.publish(new PublishIdeaVersionCommand(f.ideaId, f.author, "t3", "c3")).version();
        int v4 = service.publish(new PublishIdeaVersionCommand(f.ideaId, f.author, "t4", "c4")).version();

        assertThat(v2).isEqualTo(2);
        assertThat(v3).isEqualTo(3);
        assertThat(v4).isEqualTo(4);

        // 산 시점 스냅샷이 보존되어야 하므로 v1 row 는 절대 사라지지 않는다.
        assertThat(documentCountFor(f.ideaId)).isEqualTo(4);
    }

    @Test
    void draft_idea_allows_new_version() {
        UUID author = tx.execute(s -> UserFixture.create(userRepo));
        Long ideaId = tx.execute(s -> {
            Idea draft = ideaRepo.saveAndFlush(new Idea(author, 10, 5));
            IdeaDocument v1 = documentRepo.saveAndFlush(new IdeaDocument(draft.getId(), 1, "t1", "c1"));
            draft.updateCurrentVersion(v1.getId());
            return ideaRepo.saveAndFlush(draft).getId();
        });

        PublishIdeaVersionResult result = service.publish(
                new PublishIdeaVersionCommand(ideaId, author, "t2", "c2"));

        assertThat(result.version()).isEqualTo(2);
    }

    @Test
    void non_author_is_denied() {
        Fixture f = setupPublishedIdea();
        UUID intruder = tx.execute(s -> UserFixture.create(userRepo));

        assertThatThrownBy(() -> service.publish(
                new PublishIdeaVersionCommand(f.ideaId, intruder, "t", "c")))
                .isInstanceOf(IdeaAccessDeniedException.class);

        assertThat(documentCountFor(f.ideaId)).isEqualTo(1L);
    }

    @Test
    void archived_idea_rejects_new_version() {
        Fixture f = setupPublishedIdea();
        tx.execute(s -> {
            Idea i = ideaRepo.findById(f.ideaId).orElseThrow();
            i.archive();
            return ideaRepo.saveAndFlush(i);
        });

        assertThatThrownBy(() -> service.publish(
                new PublishIdeaVersionCommand(f.ideaId, f.author, "t", "c")))
                .isInstanceOf(IdeaNotVersionableException.class);
    }

    @Test
    void deleted_idea_rejects_new_version() {
        Fixture f = setupPublishedIdea();
        tx.execute(s -> {
            Idea i = ideaRepo.findById(f.ideaId).orElseThrow();
            i.softDelete();
            return ideaRepo.saveAndFlush(i);
        });

        assertThatThrownBy(() -> service.publish(
                new PublishIdeaVersionCommand(f.ideaId, f.author, "t", "c")))
                .isInstanceOf(IdeaNotVersionableException.class);
    }

    @Test
    void missing_idea_throws_not_found() {
        UUID author = tx.execute(s -> UserFixture.create(userRepo));

        assertThatThrownBy(() -> service.publish(
                new PublishIdeaVersionCommand(999_999L, author, "t", "c")))
                .isInstanceOf(IdeaNotFoundException.class);
    }

    /**
     * 같은 작성자가 같은 idea 에 두 스레드로 동시 새 버전 발행. idea row 의 PESSIMISTIC_WRITE 락이
     * 두 호출을 직렬화하므로 두 건 모두 성공, 버전은 {2, 3} 으로 나뉘어 겹치지 않는다.
     * UNIQUE(idea_id, version) 가 잡힐 일이 없는 게 정상 — 잡힌다면 락 패턴이 깨진 것.
     */
    @Test
    void concurrent_publishes_serialize_into_monotonic_versions() throws Exception {
        Fixture f = setupPublishedIdea();

        ExecutorService exec = Executors.newFixedThreadPool(2);
        int v1;
        int v2;
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<PublishIdeaVersionResult> a = exec.submit(() -> {
                start.await();
                return service.publish(new PublishIdeaVersionCommand(f.ideaId, f.author, "ta", "ca"));
            });
            Future<PublishIdeaVersionResult> b = exec.submit(() -> {
                start.await();
                return service.publish(new PublishIdeaVersionCommand(f.ideaId, f.author, "tb", "cb"));
            });
            start.countDown();
            v1 = a.get(15, TimeUnit.SECONDS).version();
            v2 = b.get(15, TimeUnit.SECONDS).version();
        } finally {
            exec.shutdown();
        }

        // 순서 무관하게 두 결과 버전이 정확히 {2, 3}
        assertThat(Set.of(v1, v2)).containsExactlyInAnyOrderElementsOf(List.of(2, 3));

        // 문서는 v1(셋업) + v2 + v3 = 3 개
        assertThat(documentCountFor(f.ideaId)).isEqualTo(3L);

        // current_version_id 는 v3 의 document id — 마지막에 커밋된 트랜잭션이 v3
        IdeaDocument latest = documentRepo.findFirstByIdeaIdOrderByVersionDesc(f.ideaId).orElseThrow();
        assertThat(latest.getVersion()).isEqualTo(3);
        assertThat(ideaRepo.findById(f.ideaId).orElseThrow().getCurrentVersionId())
                .isEqualTo(latest.getId());
    }

    private record Fixture(UUID author, Long ideaId) {
    }

    private Fixture setupPublishedIdea() {
        return tx.execute(status -> {
            UUID author = UserFixture.create(userRepo);
            Idea idea = IdeaFixture.createPublished(ideaRepo, documentRepo, author, 10, 5);
            return new Fixture(author, idea.getId());
        });
    }

    private long documentCountFor(Long ideaId) {
        return documentRepo.findAll().stream()
                .filter(d -> d.getIdeaId().equals(ideaId))
                .count();
    }
}
