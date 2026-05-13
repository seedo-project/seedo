package dev.seedo.idea.application;

import dev.seedo.idea.application.port.out.ChatClient;
import dev.seedo.idea.application.port.out.ChatClient.ChatTurn;
import dev.seedo.idea.application.port.out.ChatClient.IdeaDocumentDraft;
import dev.seedo.idea.domain.ChatSessionStatus;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaChatMessage;
import dev.seedo.idea.domain.IdeaChatSession;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.infrastructure.IdeaChatMessageRepository;
import dev.seedo.idea.infrastructure.IdeaChatSessionRepository;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 챗봇 세션 finalize (CLAUDE.md §8.4). LLM 이 지금까지의 대화를 한 편의 기획문서로 정리한 뒤
 * 그 결과를 한 트랜잭션에서 저장한다 (#127).
 *
 * <p>흐름:
 * <ol>
 *   <li>사전 검증 (트랜잭션 외) — 세션 lookup + 본인 / IN_PROGRESS 가드 + chat history 존재 여부 확인.
 *       빈 history 면 {@link EmptyChatHistoryException} 으로 빠르게 거부 (LLM 비용 절감 + hallucinate 회피).</li>
 *   <li>{@link ChatClient#synthesizeIdeaDocument} 호출 (트랜잭션 밖, lessons A.6 — LLM 응답까지 DB 커넥션 점유 회피).</li>
 *   <li>{@link TransactionTemplate#execute} 안에서 {@link IdeaChatSessionRepository#findByIdForUpdate} 락 +
 *       상태 재검증 (lessons A.7 — LLM 호출 동안 finalize / abandon race 차단) → idea / document INSERT →
 *       session.finalize → 이벤트 발행.</li>
 * </ol>
 *
 * <p>가격 / 보상은 MVP 플랫폼 고정 (CLAUDE.md §12). 작성자가 직접 정하는 경로는 V2 이후로 미룬다.
 *
 * <p>같은 세션에 대한 동시 finalize 는 {@code findByIdForUpdate} 의 행 락으로 직렬화된다 — 사전 검증을 두
 * 호출이 모두 통과해도 락 단계에서 한 호출만 진행, 두 번째는 상태 재검증에서 거부.
 */
@Service
public class FinalizeChatSessionService {

    /** MVP 플랫폼 고정가 (CLAUDE.md §12). */
    static final int DEFAULT_PRICE_CREDITS = 10;
    /** MVP 채택 보상 — 가격의 50% (CLAUDE.md §12). */
    static final int DEFAULT_REWARD_CREDITS = 5;

    private final IdeaChatSessionRepository sessionRepo;
    private final IdeaChatMessageRepository messageRepo;
    private final IdeaRepository ideaRepo;
    private final IdeaDocumentRepository documentRepo;
    private final ChatClient chatClient;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;

    public FinalizeChatSessionService(IdeaChatSessionRepository sessionRepo,
                                      IdeaChatMessageRepository messageRepo,
                                      IdeaRepository ideaRepo,
                                      IdeaDocumentRepository documentRepo,
                                      ChatClient chatClient,
                                      TransactionTemplate tx,
                                      ApplicationEventPublisher events) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.ideaRepo = ideaRepo;
        this.documentRepo = documentRepo;
        this.chatClient = chatClient;
        this.tx = tx;
        this.events = events;
    }

    public FinalizeChatSessionResult finalize(FinalizeChatSessionCommand cmd) {
        // 1. 사전 검증 — 빠른 거부로 LLM 비용 / 응답 시간 절감.
        IdeaChatSession session = sessionRepo.findById(cmd.sessionId())
                .orElseThrow(() -> new ChatSessionNotFoundException(cmd.sessionId()));
        if (!session.getUserId().equals(cmd.actor())) {
            throw new ChatSessionAccessDeniedException(cmd.sessionId(), cmd.actor());
        }
        if (session.getStatus() != ChatSessionStatus.IN_PROGRESS) {
            throw new ChatSessionNotFinalizableException(cmd.sessionId(), session.getStatus());
        }

        List<IdeaChatMessage> history = messageRepo.findBySessionIdOrderByCreatedAtAscIdAsc(cmd.sessionId());
        if (history.isEmpty()) {
            throw new EmptyChatHistoryException(cmd.sessionId());
        }

        // 2. LLM 호출 — 트랜잭션 밖. 실패 시 예외 전파, 아래 INSERT 단계 도달 안 함.
        List<ChatTurn> turns = history.stream()
                .map(m -> new ChatTurn(m.getRole(), m.getContent()))
                .toList();
        IdeaDocumentDraft draft = chatClient.synthesizeIdeaDocument(turns);

        long historySnapshotSize = history.size();

        // 3. INSERT 트랜잭션 — 세션 락 + 상태 재검증 + history 변경 가드 + idea/document INSERT + 세션 FINALIZED.
        return tx.execute(status -> {
            IdeaChatSession locked = sessionRepo.findByIdForUpdate(cmd.sessionId())
                    .orElseThrow(() -> new ChatSessionNotFoundException(cmd.sessionId()));
            if (locked.getStatus() != ChatSessionStatus.IN_PROGRESS) {
                // LLM 호출 동안 다른 트랜잭션이 finalize / abandon 한 race — LLM 응답은 버려짐.
                throw new ChatSessionNotFinalizableException(cmd.sessionId(), locked.getStatus());
            }
            // LLM 호출 동안 새 메시지가 끼어들면 LLM draft 가 최신 대화를 반영 못 한다 — 명시적으로 거부해
            // 클라이언트가 새 history 로 다시 finalize 호출하게 한다 (메시지는 append-only 라 size 비교로 충분).
            long currentHistorySize = messageRepo.countBySessionId(cmd.sessionId());
            if (currentHistorySize != historySnapshotSize) {
                throw new ChatHistoryChangedException(cmd.sessionId(), historySnapshotSize, currentHistorySize);
            }

            Idea idea = ideaRepo.saveAndFlush(
                    new Idea(cmd.actor(), DEFAULT_PRICE_CREDITS, DEFAULT_REWARD_CREDITS));
            IdeaDocument doc = documentRepo.saveAndFlush(
                    new IdeaDocument(idea.getId(), 1, draft.title(), draft.contentMd()));
            idea.updateCurrentVersion(doc.getId());

            locked.finalize(idea.getId());

            events.publishEvent(new IdeaVersionPublishedEvent(idea.getId(), doc.getId(), doc.getVersion()));
            return new FinalizeChatSessionResult(idea.getId(), doc.getId(), doc.getVersion());
        });
    }
}
