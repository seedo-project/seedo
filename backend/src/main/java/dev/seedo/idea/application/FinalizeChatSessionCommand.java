package dev.seedo.idea.application;

import java.util.UUID;

/**
 * finalize 호출 입력. 본문(title / contentMd) 은 service 가 chat history 를 LLM 에 보내 자동 생성한다
 * (#127, CLAUDE.md §11). 클라이언트는 어떤 세션을 finalize 할지만 알려주면 된다.
 */
public record FinalizeChatSessionCommand(
        long sessionId,
        UUID actor
) {
}
