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
 *   <li>사전 가드 — 세션 lookup + 권한·상태 검증 (404 / 403 / 409). 명백히 종료된 세션이면 LLM 호출 자체를
 *       하지 않고 빠르게 거부 (외부 비용 절감 + 응답 시간 ↓).</li>
 *   <li>지난 메시지 시간순 조회 → 시스템 프롬프트 + history + 새 USER 로 컨텍스트 빌드</li>
 *   <li>{@link ChatClient} 호출 — <b>트랜잭션 밖</b> (lessons A.6: DB 커넥션 점유 회피)</li>
 *   <li>{@link TransactionTemplate} 안에서 <b>세션 row 락 + 상태 재검증</b> 후 USER + ASSISTANT 두 row INSERT.
 *       LLM 호출 동안 다른 트랜잭션이 finalize / abandon 했을 가능성 (TOCTOU) 을 락으로 잡는다.</li>
 * </ol>
 *
 * <p>메서드에 {@code @Transactional} 을 안 박는다 — LLM 호출이 트랜잭션 안에 들어가면 응답까지 (수 초)
 * DB 커넥션을 점유한다. 동시 챗봇 호출이 늘 때 커넥션 풀 고갈 위험 (lessons A.6).
 *
 * <p>가드 재검증 (사전 + INSERT 단계) 두 번 하는 이유: 사전 가드는 사용자 체감 응답 시간을 위해, INSERT 단계
 * 재검증은 lessons A.3 (상태 전이 리소스 PESSIMISTIC_WRITE) 로 race 차단. race 경로에 도달한 LLM 응답은
 * 버려진다 — 극히 드물지만, 정합성 우선.
 */
@Service
public class SendChatMessageService {

    /** 챗봇 종료 신호 — FE 가 응답 본문에 이 문장이 포함되어 있는지 contains() 로 감지해 finalize 버튼을
     *  활성화한다. LLM 이 정확한 문구로 응답하도록 시스템 프롬프트에 그대로 인용해 주입 (#173). */
    static final String FINISH_TRIGGER_PHRASE = "충분히 이야기 나눴어요! 아이디어를 만들어볼게요 ✨";

    /**
     * 챗봇 인터뷰 시스템 프롬프트. 5가지 정보 (Problem / Solution / Target User / Market / Insight) 를
     * 한 번에 한 가지씩 수집하도록 가이드. finalize 단계의 5개 섹션 (page S303) 과 짝.
     *
     * <p>5가지 필수 정보가 모두 모이면 정확한 {@link #FINISH_TRIGGER_PHRASE} 를 응답 마지막에 포함하도록
     * 지시 (#173). FE 는 그 문장을 keyword 매칭해서 finalize 버튼 활성화. 정보가 부족하면 절대 출력 금지.
     */
    static final String SYSTEM_PROMPT = """
            당신은 사용자의 막연한 아이디어를 한 편의 기획문서로 발전시키는 인터뷰어입니다.
            아래 5가지 정보를 한 번에 한 가지씩, 자연스러운 대화 흐름으로 수집하세요.

            [필수 수집 정보]
            1. Problem      — 사용자가 겪은 구체적인 불편함 / 상황
            2. Solution     — 어떻게 해결하고 싶은지
            3. Target User  — 누가 이 솔루션을 쓸까
            4. Market       — 비슷한 고민을 가진 사람이 얼마나 많은가
            5. Insight      — 왜 지금 이 솔루션이 의미 있는가

            [선택 수집 정보 — 필수가 모인 뒤 자연스러우면 추가로]
            - 비슷한 서비스를 써본 적 있는지
            - 돈을 낼 의향이 있는지
            - 언제 / 어떤 상황에서 사용할 것 같은지

            [대화 규칙]
            - 한 메시지에 한 가지 질문만. 여러 항목을 한꺼번에 묻지 마세요.
            - 사용자 답변이 모호하면 한 번 더 깊이 파고드는 후속 질문을 하세요 (예시 / 빈도 / 강도).
            - 답변은 간결한 한국어 문장 1~3줄. 마크다운 / 불릿 / 따옴표 금지.
            - 사용자가 답하지 않은 사실을 추측해 채우지 마세요.

            [종료 신호 — 매우 중요]
            5가지 필수 정보가 모두 충분히 수집되었다고 판단될 때만, 응답의 마지막 줄에 정확히 다음 문장을
            그대로 포함하세요 (따옴표는 빼고 문장 자체만):

            충분히 이야기 나눴어요! 아이디어를 만들어볼게요 ✨

            클라이언트가 이 문장을 감지해 finalize 버튼을 활성화합니다.
            - 5가지 정보가 단 하나라도 부족하면 이 문장을 절대 출력하지 말고, 부족한 부분을 한두 질문 더 던지세요.
            - 대화가 10턴 이상이라도 정보가 부족하면 종료 신호를 내지 마세요 — 정확성이 우선.
            - 문구를 변형하지 마세요. 의미는 같아도 다른 표현 (예: "정리해볼게요", "이만 마무리할게요") 은
              클라이언트가 감지하지 못합니다.
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

        // INSERT 트랜잭션: 세션 row 락 + 상태 재검증 → 두 row INSERT.
        // LLM 호출 동안 (수 초) 다른 트랜잭션이 finalize / abandon 한 race 를 락으로 차단.
        IdeaChatMessage assistant = tx.execute(status -> {
            IdeaChatSession locked = sessionRepo.findByIdForUpdate(cmd.sessionId())
                    .orElseThrow(() -> new ChatSessionNotFoundException(cmd.sessionId()));
            // 사전 가드를 통과한 세션 + 동일 user → 본인 검증은 사전과 동치 (소유자 변경 경로 없음). status 만 재확인.
            if (locked.getStatus() != ChatSessionStatus.IN_PROGRESS) {
                throw new ChatSessionNotAcceptingMessagesException(cmd.sessionId(), locked.getStatus());
            }
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
