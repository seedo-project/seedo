package dev.seedo.idea.application.port.out;

import dev.seedo.idea.domain.ChatMessageRole;

import java.util.List;

/**
 * 챗봇 외부 호출 포트 (CLAUDE.md backend "선택적 헥사고날 — 외부 통합만"). 도메인 어휘로:
 * "지금까지의 대화 묶음을 주면 다음 assistant 응답 한 덩어리를 돌려준다". 구현체는
 * {@code infrastructure/openai/OpenAiChatAdapter}.
 *
 * <p>테스트는 이 인터페이스를 stub 빈으로 덮어써 실제 OpenAI 호출 없이 결정적 응답을 만든다.
 */
public interface ChatClient {

    /**
     * @param turns 시간순 — 가장 오래된 메시지가 첫 번째. 시스템 프롬프트가 있다면 caller 가 첫 turn 에
     *              넣어 보낸다 (port 가 자동 주입하지 않는다 — 도메인 의존 / 어휘 격리 위해).
     * @return assistant 응답 본문. 비어있을 수 없다 (어댑터가 빈 응답이면 예외).
     * @throws RuntimeException 외부 호출 실패. 호출자가 잡아 처리 — 챗봇 호출은 사용자 흐름의 중심이라
     *                          실패 시 4xx/5xx 로 노출되어야 한다 (임베딩처럼 swallow 하지 않음).
     */
    String complete(List<ChatTurn> turns);

    /** 한 메시지 — 역할 (USER/ASSISTANT/SYSTEM) + 본문. */
    record ChatTurn(ChatMessageRole role, String content) {
    }
}
