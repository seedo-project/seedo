package dev.seedo.idea.application.port.out;

/**
 * 임베딩 외부 호출 포트 (CLAUDE.md backend "선택적 헥사고날 — 외부 통합만"). 도메인 어휘로:
 * "이 문자열에 해당하는 벡터를 받아온다". 구현체는 {@code infrastructure/openai/OpenAiEmbeddingAdapter}.
 *
 * <p>테스트는 이 인터페이스를 stub 빈으로 덮어써 실제 OpenAI 호출 없이 검증한다.
 */
public interface EmbeddingClient {

    /**
     * 차원: 현재 모델 {@code text-embedding-3-small} 기준 1536. 모델이 바뀌면 {@code idea_embeddings.embedding}
     * 컬럼의 vector(N) 도 같이 바꾸고 전체 재인덱싱이 필요하다 (CLAUDE.md §11).
     *
     * @throws RuntimeException 외부 호출 실패 (네트워크 / 4xx / 5xx). 호출자가 잡아 처리 — 임베딩은 부가
     *                          기능이라 사용자 흐름은 영향 받지 않는다.
     */
    float[] embed(String text);
}
