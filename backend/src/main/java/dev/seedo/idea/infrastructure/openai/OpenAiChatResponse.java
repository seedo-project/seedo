package dev.seedo.idea.infrastructure.openai;

import java.util.List;

/**
 * OpenAI {@code POST /v1/chat/completions} 응답 일부. 한 turn 응답만 받는다고 가정 — {@code choices} 는 1 개 원소.
 */
record OpenAiChatResponse(List<Choice> choices) {

    record Choice(Message message) {
    }

    record Message(String role, String content) {
    }
}
