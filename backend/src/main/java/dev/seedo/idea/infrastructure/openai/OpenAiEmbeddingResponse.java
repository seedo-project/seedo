package dev.seedo.idea.infrastructure.openai;

import java.util.List;

/**
 * OpenAI {@code POST /v1/embeddings} 응답 일부. 한 번에 한 입력만 보낸다고 가정 — {@code data} 는 1 개 원소.
 */
record OpenAiEmbeddingResponse(List<Datum> data) {

    record Datum(float[] embedding) {
    }
}
