package dev.seedo.idea.application;

import dev.seedo.idea.domain.ChatSessionStatus;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaChatSession;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.infrastructure.IdeaChatSessionRepository;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 챗봇 세션 finalize (CLAUDE.md §8.4). 네 갱신을 한 트랜잭션에서 처리:
 * <ol>
 *   <li>{@code ideas} INSERT (DRAFT, 본인 author_id, 플랫폼 고정 가격/보상)</li>
 *   <li>{@code idea_documents} INSERT (version=1, 클라이언트 입력 title/content_md)</li>
 *   <li>{@code ideas.current_version_id} UPDATE → 새 document.id</li>
 *   <li>{@code idea_chat_sessions} → FINALIZED 전이 (idea_id, finalized_at 동시에 설정)</li>
 * </ol>
 *
 * <p>가격/보상은 MVP 플랫폼 고정 (CLAUDE.md §12). 작성자가 직접 정하는 경로는 V2 이후로 미룬다.
 *
 * <p>본문 LLM 생성, 임베딩 실제 계산은 이번 PR 범위 밖. 트랜잭션 커밋 후 {@link IdeaVersionPublishedEvent}
 * 를 발행해 다음 PR 의 listener 가 받게 한다.
 *
 * <p>같은 세션에 대한 동시 finalize 는 {@link IdeaChatSessionRepository#findByIdForUpdate} 의 행 락으로
 * 직렬화된다. 락이 없으면 두 호출이 모두 IN_PROGRESS 검증을 통과해 고아 idea/idea_documents 가 만들어진다
 * — V2 의 CHECK 는 단일 row 정합성만 강제하므로 고아 idea 는 잡지 못한다.
 *
 * <p>마지막 방어선: V2 의 chat_sessions CHECK ((status='FINALIZED') = (idea_id IS NOT NULL))
 * 와 짝을 이룬다. 도메인의 {@link IdeaChatSession#finalize} 가 셋을 한 번에 설정하므로 정상 흐름에서는 통과.
 */
@Service
public class FinalizeChatSessionService {

    /** MVP 플랫폼 고정가 (CLAUDE.md §12). 추후 작성자 입력 또는 카테고리별로 분기 가능. */
    static final int DEFAULT_PRICE_CREDITS = 10;
    /** MVP 채택 보상 — 가격의 50% (CLAUDE.md §12). */
    static final int DEFAULT_REWARD_CREDITS = 5;

    private final IdeaChatSessionRepository sessionRepo;
    private final IdeaRepository ideaRepo;
    private final IdeaDocumentRepository documentRepo;
    private final ApplicationEventPublisher events;

    public FinalizeChatSessionService(IdeaChatSessionRepository sessionRepo,
                                      IdeaRepository ideaRepo,
                                      IdeaDocumentRepository documentRepo,
                                      ApplicationEventPublisher events) {
        this.sessionRepo = sessionRepo;
        this.ideaRepo = ideaRepo;
        this.documentRepo = documentRepo;
        this.events = events;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FinalizeChatSessionResult finalize(FinalizeChatSessionCommand cmd) {
        IdeaChatSession session = sessionRepo.findByIdForUpdate(cmd.sessionId())
                .orElseThrow(() -> new ChatSessionNotFoundException(cmd.sessionId()));
        if (!session.getUserId().equals(cmd.actor())) {
            throw new ChatSessionAccessDeniedException(cmd.sessionId(), cmd.actor());
        }
        if (session.getStatus() != ChatSessionStatus.IN_PROGRESS) {
            throw new ChatSessionNotFinalizableException(cmd.sessionId(), session.getStatus());
        }

        Idea idea = ideaRepo.saveAndFlush(new Idea(cmd.actor(), DEFAULT_PRICE_CREDITS, DEFAULT_REWARD_CREDITS));
        IdeaDocument doc = documentRepo.saveAndFlush(
                new IdeaDocument(idea.getId(), 1, cmd.title(), cmd.contentMd()));
        idea.updateCurrentVersion(doc.getId());

        session.finalize(idea.getId());

        events.publishEvent(new IdeaVersionPublishedEvent(idea.getId(), doc.getId(), doc.getVersion()));
        return new FinalizeChatSessionResult(idea.getId(), doc.getId(), doc.getVersion());
    }
}
