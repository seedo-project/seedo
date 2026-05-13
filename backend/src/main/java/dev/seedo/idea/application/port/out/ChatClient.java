package dev.seedo.idea.application.port.out;

import dev.seedo.idea.domain.ChatMessageRole;

import java.util.List;

/**
 * 챗봇 외부 호출 포트 (CLAUDE.md backend "선택적 헥사고날 — 외부 통합만"). 도메인 어휘로:
 * "지금까지의 대화 묶음을 주면 다음 응답 또는 정리된 기획문서를 돌려준다". 구현체는
 * {@code infrastructure/openai/OpenAiChatAdapter}.
 *
 * <p>테스트는 이 인터페이스를 stub 빈으로 덮어써 실제 OpenAI 호출 없이 결정적 응답을 만든다.
 */
public interface ChatClient {

    /**
     * 다음 assistant turn 응답 한 덩어리. 챗봇 대화의 일반 흐름에서 사용.
     *
     * @param turns 시간순 — 가장 오래된 메시지가 첫 번째. 시스템 프롬프트가 있다면 caller 가 첫 turn 에
     *              넣어 보낸다 (port 가 자동 주입하지 않는다 — 도메인 의존 / 어휘 격리 위해).
     * @return assistant 응답 본문. 비어있을 수 없다 (어댑터가 빈 응답이면 예외).
     * @throws RuntimeException 외부 호출 실패. 호출자가 잡아 처리 — 챗봇 호출은 사용자 흐름의 중심이라
     *                          실패 시 4xx/5xx 로 노출되어야 한다 (임베딩처럼 swallow 하지 않음).
     */
    String complete(List<ChatTurn> turns);

    /**
     * 지금까지의 대화 묶음을 한 편의 기획문서 초안으로 정리. finalize 흐름에서 사용 (CLAUDE.md §11).
     *
     * <p>구현체는 LLM 의 JSON mode 등으로 구조화 출력을 보장해야 한다 — 호출자는 {@link IdeaDocumentDraft}
     * 두 필드를 그대로 DB 에 저장한다. title 빈 문자열 / null, contentMd 빈 문자열 / null 은 어댑터가
     * 예외로 변환 (호출자가 빈 본문을 저장하지 않도록).
     *
     * @param turns 시간순. 시스템 프롬프트는 caller 가 넣지 않아도 어댑터가 finalize 전용 프롬프트를 주입.
     */
    IdeaDocumentDraft synthesizeIdeaDocument(List<ChatTurn> turns);

    /** 한 메시지 — 역할 (USER/ASSISTANT/SYSTEM) + 본문. */
    record ChatTurn(ChatMessageRole role, String content) {
    }

    /**
     * finalize 자동 작성 결과. title 은 한 줄(최대 200자, V2 {@code idea_documents.title varchar(200)} 과 일치),
     * contentMd 는 마크다운.
     */
    record IdeaDocumentDraft(String title, String contentMd) {
    }
}
