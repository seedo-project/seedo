package dev.seedo.idea.application;

import dev.seedo.idea.application.port.out.ChatClient;
import dev.seedo.idea.application.port.out.ChatClient.ChatTurn;
import dev.seedo.idea.domain.ChatMessageRole;
import dev.seedo.idea.domain.ChatSessionStatus;
import dev.seedo.idea.domain.IdeaChatMessage;
import dev.seedo.idea.domain.IdeaChatSession;
import dev.seedo.idea.infrastructure.IdeaChatMessageRepository;
import dev.seedo.idea.infrastructure.IdeaChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 챗봇 한 turn 동기 응답 (CLAUDE.md §7 Spring 책임, §11 gpt-4o-mini).
 *
 * <p>흐름:
 * <ol>
 *   <li>입력 검증 — 빈/공백 → {@link ChatMessageEmptyException}</li>
 *   <li>세션 lookup + 권한·상태 가드 (404 / 403 / 409)</li>
 *   <li>지난 메시지 시간순 조회 → 시스템 프롬프트 + history + 새 USER 로 컨텍스트 빌드</li>
 *   <li>{@link ChatClient} 호출 — <b>트랜잭션 밖</b> (lessons A.6: DB 커넥션 점유 회피)</li>
 *   <li>USER + ASSISTANT 두 row 를 {@link TransactionTemplate} 안에서 한 트랜잭션으로 INSERT.
 *       LLM 호출 자체가 실패하면 이 단계는 도달 안 함 — USER 도 안 들어가 사용자가 재시도하면 깨끗한 상태.</li>
 * </ol>
 *
 * <p>메서드에 {@code @Transactional} 을 안 박는다 — LLM 호출이 트랜잭션 안에 들어가면 응답까지 (수 초)
 * DB 커넥션을 점유한다. 동시 챗봇 호출이 늘 때 커넥션 풀 고갈 위험.
 */
@Service
public class SendChatMessageService {

    /**
     * 첫 PR 의 단순 시스템 프롬프트. 한국어 + 사용자 니즈 구체화 유도. few-shot / 페르소나 세팅은 후속 PR
     * 에서 별도 튜닝.
     */
    static final String SYSTEM_PROMPT = """
            당신은 사용자가 가진 막연한 아이디어를 듣고 구체적인 기획문서로 발전시키는 도우미입니다.
            한 번에 한 가지 질문만 던져 사용자가 생각을 정리할 수 있게 돕습니다.
            답변은 간결한 한국어 문장으로, 마크다운을 사용하지 마세요.
            """;

    private final IdeaChatSessionRepository sessionRepo;
    private final IdeaChatMessageRepository messageRepo;
    private final ChatClient chatClient;
    private final TransactionTemplate tx;

    public SendChatMessageService(IdeaChatSessionRepository sessionRepo,
                                  IdeaChatMessageRepository messageRepo,
                                  ChatClient chatClient,
                                  TransactionTemplate tx) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.chatClient = chatClient;
        this.tx = tx;
    }

    public SendChatMessageResult send(SendChatMessageCommand cmd) {
        if (cmd.content() == null || cmd.content().isBlank()) {
            throw new ChatMessageEmptyException();
        }
        String userText = cmd.content().trim();

        IdeaChatSession session = sessionRepo.findById(cmd.sessionId())
                .orElseThrow(() -> new ChatSessionNotFoundException(cmd.sessionId()));
        if (!session.getUserId().equals(cmd.userId())) {
            throw new ChatSessionAccessDeniedException(cmd.sessionId(), cmd.userId());
        }
        if (session.getStatus() != ChatSessionStatus.IN_PROGRESS) {
            throw new ChatSessionNotAcceptingMessagesException(cmd.sessionId(), session.getStatus());
        }

        List<IdeaChatMessage> history = messageRepo.findBySessionIdOrderByCreatedAtAscIdAsc(cmd.sessionId());
        List<ChatTurn> turns = buildContext(history, userText);

        // 외부 호출 — 트랜잭션 밖. 실패 시 예외 전파, 아래 INSERT 도달 안 함.
        String assistantText = chatClient.complete(turns);

        // INSERT 는 한 트랜잭션 — 둘 중 하나만 들어가는 부분 실패 회피.
        IdeaChatMessage assistant = tx.execute(status -> {
            messageRepo.save(new IdeaChatMessage(cmd.sessionId(), ChatMessageRole.USER, userText));
            return messageRepo.save(new IdeaChatMessage(cmd.sessionId(), ChatMessageRole.ASSISTANT, assistantText));
        });

        return new SendChatMessageResult(
                assistant.getId(),
                assistant.getContent(),
                assistant.getCreatedAt()
        );
    }

    private static List<ChatTurn> buildContext(List<IdeaChatMessage> history, String newUserText) {
        List<ChatTurn> turns = new ArrayList<>(history.size() + 2);
        turns.add(new ChatTurn(ChatMessageRole.SYSTEM, SYSTEM_PROMPT));
        for (IdeaChatMessage msg : history) {
            turns.add(new ChatTurn(msg.getRole(), msg.getContent()));
        }
        turns.add(new ChatTurn(ChatMessageRole.USER, newUserText));
        return turns;
    }
}
