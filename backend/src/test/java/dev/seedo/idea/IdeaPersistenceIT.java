package dev.seedo.idea;

import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.domain.IdeaStatus;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.user.domain.User;
import dev.seedo.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idea + IdeaDocument 의 JPA save/find 라운드트립과 상태 전이 메서드 검증.
 * <p>V2 invariant IT 는 native query 기반이라 JPA 매핑 자체는 검증하지 못한다 — 여기서 보강.
 */
@Transactional
class IdeaPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository docRepo;

    @Test
    void save_default_idea_round_trip() {
        UUID author = createUser();

        Idea idea = ideaRepo.saveAndFlush(new Idea(author, 10, 5));
        ideaRepo.flush();
        Long id = idea.getId();

        Idea found = ideaRepo.findById(id).orElseThrow();
        assertThat(found.getAuthorId()).isEqualTo(author);
        assertThat(found.getStatus()).isEqualTo(IdeaStatus.DRAFT);
        assertThat(found.getPriceCredits()).isEqualTo(10);
        assertThat(found.getRewardCredits()).isEqualTo(5);
        assertThat(found.getCurrentVersionId()).isNull();
        assertThat(found.getDeletedAt()).isNull();
    }

    @Test
    void state_transitions_persist() {
        UUID author = createUser();
        Idea idea = ideaRepo.saveAndFlush(new Idea(author, 10, 5));

        idea.publish();
        ideaRepo.saveAndFlush(idea);
        assertThat(ideaRepo.findById(idea.getId()).orElseThrow().getStatus()).isEqualTo(IdeaStatus.PUBLISHED);

        idea.archive();
        ideaRepo.saveAndFlush(idea);
        assertThat(ideaRepo.findById(idea.getId()).orElseThrow().getStatus()).isEqualTo(IdeaStatus.ARCHIVED);

        idea.softDelete();
        ideaRepo.saveAndFlush(idea);
        Idea reloaded = ideaRepo.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IdeaStatus.DELETED);
        assertThat(reloaded.getDeletedAt()).isNotNull();
    }

    @Test
    void document_save_and_current_version_pointer() {
        // V2 finalize 시퀀스 모사 (§8.4): idea + doc INSERT → ideas.current_version_id UPDATE
        UUID author = createUser();
        Idea idea = ideaRepo.saveAndFlush(new Idea(author, 10, 5));

        IdeaDocument doc = docRepo.saveAndFlush(
                new IdeaDocument(idea.getId(), 1, "타이틀 v1", "본문 v1"));

        idea.updateCurrentVersion(doc.getId());
        ideaRepo.saveAndFlush(idea);

        Idea reloaded = ideaRepo.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getCurrentVersionId()).isEqualTo(doc.getId());

        IdeaDocument foundDoc = docRepo.findById(doc.getId()).orElseThrow();
        assertThat(foundDoc.getIdeaId()).isEqualTo(idea.getId());
        assertThat(foundDoc.getVersion()).isEqualTo(1);
        assertThat(foundDoc.getTitle()).isEqualTo("타이틀 v1");
        assertThat(foundDoc.getContentMd()).isEqualTo("본문 v1");
        assertThat(foundDoc.getCreatedAt()).isNotNull();
    }

    @Test
    void next_version_query_returns_max() {
        UUID author = createUser();
        Idea idea = ideaRepo.saveAndFlush(new Idea(author, 10, 5));

        docRepo.saveAndFlush(new IdeaDocument(idea.getId(), 1, "v1", "c1"));
        docRepo.saveAndFlush(new IdeaDocument(idea.getId(), 2, "v2", "c2"));
        docRepo.saveAndFlush(new IdeaDocument(idea.getId(), 3, "v3", "c3"));

        IdeaDocument latest = docRepo.findFirstByIdeaIdOrderByVersionDesc(idea.getId()).orElseThrow();
        assertThat(latest.getVersion()).isEqualTo(3);
    }

    @Test
    void find_by_id_for_update_returns_idea() {
        UUID author = createUser();
        Idea idea = ideaRepo.saveAndFlush(new Idea(author, 10, 5));

        Idea locked = ideaRepo.findByIdForUpdate(idea.getId()).orElseThrow();
        assertThat(locked.getId()).isEqualTo(idea.getId());
        // 락 자체는 트랜잭션 안에서만 의미 있음 — 동시성 검증은 별도 IT 가 필요하면 추가
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        userRepo.saveAndFlush(new User(id, "u-" + id + "@test", "n-" + id.toString().substring(0, 8)));
        return id;
    }
}
