package dev.seedo.idea.application;

import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.domain.IdeaStatus;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 아이디어 본문 새 버전 발행 (CLAUDE.md §8.4):
 * <ol>
 *   <li>{@code ideas} SELECT FOR UPDATE — 동시 발행을 직렬화 (MAX(version)+1 계산 보호)</li>
 *   <li>작성자·상태 검증 (DRAFT / PUBLISHED 만 허용)</li>
 *   <li>{@code idea_documents} INSERT (version = MAX+1) — UNIQUE(idea_id, version) 가 마지막 방어선</li>
 *   <li>{@code ideas.current_version_id} UPDATE → 새 document.id</li>
 * </ol>
 *
 * <p>버전 1 은 {@link FinalizeChatSessionService} 만 만든다. 이 service 는 항상 version ≥ 2 — finalize 가 한 번도
 * 안 일어난 idea 에는 도달할 수 없다 (idea 가 만들어지는 경로 자체가 finalize 뿐).
 */
@Service
public class PublishIdeaVersionService {

    private final IdeaRepository ideaRepo;
    private final IdeaDocumentRepository documentRepo;
    private final ApplicationEventPublisher events;

    public PublishIdeaVersionService(IdeaRepository ideaRepo,
                                     IdeaDocumentRepository documentRepo,
                                     ApplicationEventPublisher events) {
        this.ideaRepo = ideaRepo;
        this.documentRepo = documentRepo;
        this.events = events;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PublishIdeaVersionResult publish(PublishIdeaVersionCommand cmd) {
        Idea idea = ideaRepo.findByIdForUpdate(cmd.ideaId())
                .orElseThrow(() -> new IdeaNotFoundException(cmd.ideaId()));
        if (!idea.getAuthorId().equals(cmd.actor())) {
            throw new IdeaAccessDeniedException(cmd.ideaId(), cmd.actor());
        }
        if (idea.getStatus() != IdeaStatus.DRAFT && idea.getStatus() != IdeaStatus.PUBLISHED) {
            throw new IdeaNotVersionableException(cmd.ideaId(), idea.getStatus());
        }

        int nextVersion = documentRepo.findFirstByIdeaIdOrderByVersionDesc(cmd.ideaId())
                .map(IdeaDocument::getVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "idea has no documents — finalize missing? ideaId=" + cmd.ideaId())) + 1;

        IdeaDocument doc = documentRepo.saveAndFlush(
                new IdeaDocument(cmd.ideaId(), nextVersion, cmd.title(), cmd.contentMd()));
        idea.updateCurrentVersion(doc.getId());

        // 새 버전 발행은 keywords 를 갱신하지 않는다 — finalize 가 추출한 이전 키워드를 보존.
        // listener 가 빈 List 를 받으면 keywords upsert 를 skip (embedding 만 갱신).
        events.publishEvent(new IdeaVersionPublishedEvent(
                idea.getId(), doc.getId(), doc.getVersion(), List.of()));
        return new PublishIdeaVersionResult(idea.getId(), doc.getId(), doc.getVersion());
    }
}
