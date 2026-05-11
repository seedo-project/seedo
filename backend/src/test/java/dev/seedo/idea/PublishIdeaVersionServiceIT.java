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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PublishIdeaVersionService} 의 트랜잭션 무결성 + 소유권 / 상태 가드.
 *
 * <p>버전 단조성: MAX(version)+1 로 새 row 가 쌓이고 {@code current_version_id} 는 항상 최신 row 를 가리킨다.
 * UNIQUE(idea_id, version) 는 DB 최후 방어선 — 정상 호출 흐름에서는 service 의 SELECT FOR UPDATE 직렬화가 잡는다.
 */
@Transactional
class PublishIdeaVersionServiceIT extends AbstractIntegrationTest {

    @Autowired
    private PublishIdeaVersionService service;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository documentRepo;

    @Test
    void publish_v2_increments_version_and_moves_current_pointer() {
        UUID author = UserFixture.create(userRepo);
        Idea seeded = IdeaFixture.createPublished(ideaRepo, documentRepo, author, 10, 5);
        Long v1DocId = seeded.getCurrentVersionId();

        PublishIdeaVersionResult result = service.publish(
                new PublishIdeaVersionCommand(seeded.getId(), author, "t2", "c2"));

        assertThat(result.version()).isEqualTo(2);
        assertThat(result.documentId()).isNotEqualTo(v1DocId);

        Idea reloaded = ideaRepo.findById(seeded.getId()).orElseThrow();
        assertThat(reloaded.getCurrentVersionId()).isEqualTo(result.documentId());

        IdeaDocument v2 = documentRepo.findById(result.documentId()).orElseThrow();
        assertThat(v2.getIdeaId()).isEqualTo(seeded.getId());
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.getTitle()).isEqualTo("t2");
        assertThat(v2.getContentMd()).isEqualTo("c2");
    }

    @Test
    void successive_publishes_produce_monotonic_versions() {
        UUID author = UserFixture.create(userRepo);
        Idea seeded = IdeaFixture.createPublished(ideaRepo, documentRepo, author, 10, 5);

        int v2 = service.publish(new PublishIdeaVersionCommand(seeded.getId(), author, "t2", "c2")).version();
        int v3 = service.publish(new PublishIdeaVersionCommand(seeded.getId(), author, "t3", "c3")).version();
        int v4 = service.publish(new PublishIdeaVersionCommand(seeded.getId(), author, "t4", "c4")).version();

        assertThat(v2).isEqualTo(2);
        assertThat(v3).isEqualTo(3);
        assertThat(v4).isEqualTo(4);

        // 산 시점 스냅샷이 보존되어야 하므로 v1 row 는 절대 사라지지 않는다.
        assertThat(documentRepo.count()).isEqualTo(4);
    }

    @Test
    void draft_idea_allows_new_version() {
        UUID author = UserFixture.create(userRepo);
        Idea draft = ideaRepo.saveAndFlush(new Idea(author, 10, 5));
        IdeaDocument v1 = documentRepo.saveAndFlush(new IdeaDocument(draft.getId(), 1, "t1", "c1"));
        draft.updateCurrentVersion(v1.getId());

        PublishIdeaVersionResult result = service.publish(
                new PublishIdeaVersionCommand(draft.getId(), author, "t2", "c2"));

        assertThat(result.version()).isEqualTo(2);
    }

    @Test
    void non_author_is_denied() {
        UUID author = UserFixture.create(userRepo);
        UUID intruder = UserFixture.create(userRepo);
        Idea seeded = IdeaFixture.createPublished(ideaRepo, documentRepo, author, 10, 5);

        assertThatThrownBy(() -> service.publish(
                new PublishIdeaVersionCommand(seeded.getId(), intruder, "t", "c")))
                .isInstanceOf(IdeaAccessDeniedException.class);

        assertThat(documentRepo.count()).isEqualTo(1);
    }

    @Test
    void archived_idea_rejects_new_version() {
        UUID author = UserFixture.create(userRepo);
        Idea seeded = IdeaFixture.createPublished(ideaRepo, documentRepo, author, 10, 5);
        seeded.archive();
        ideaRepo.saveAndFlush(seeded);

        assertThatThrownBy(() -> service.publish(
                new PublishIdeaVersionCommand(seeded.getId(), author, "t", "c")))
                .isInstanceOf(IdeaNotVersionableException.class);
    }

    @Test
    void deleted_idea_rejects_new_version() {
        UUID author = UserFixture.create(userRepo);
        Idea seeded = IdeaFixture.createPublished(ideaRepo, documentRepo, author, 10, 5);
        seeded.softDelete();
        ideaRepo.saveAndFlush(seeded);

        assertThatThrownBy(() -> service.publish(
                new PublishIdeaVersionCommand(seeded.getId(), author, "t", "c")))
                .isInstanceOf(IdeaNotVersionableException.class);
    }

    @Test
    void missing_idea_throws_not_found() {
        UUID author = UserFixture.create(userRepo);

        assertThatThrownBy(() -> service.publish(
                new PublishIdeaVersionCommand(999_999L, author, "t", "c")))
                .isInstanceOf(IdeaNotFoundException.class);
    }
}
