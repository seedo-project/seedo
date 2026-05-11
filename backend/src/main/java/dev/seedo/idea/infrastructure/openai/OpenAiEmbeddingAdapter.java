package dev.seedo.idea.infrastructure.openai;

import dev.seedo.idea.application.port.out.EmbeddingClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * OpenAI {@code text-embedding-3-small} 어댑터 (CLAUDE.md §11). WebClient + Resilience4j 서킷/재시도 보호.
 *
 * <p>이 어댑터는 {@code dev.seedo.idea.application.port.out.EmbeddingClient} 의 유일한 구현체.
 * 도메인 / application 은 인터페이스만 알고, OpenAI 구체 사항 (URL, 모델명, DTO) 은 이 패키지에 격리.
 *
 * <p>호출 실패는 호출자 ({@code IdeaEmbeddingRefreshListener}) 가 잡는다 — 임베딩은 사용자 흐름에 영향
 * 없는 부가 기능이라 적절히 로깅 후 drop. 서킷 OPEN 일 때도 RuntimeException 으로 전파된다.
 */
@Component
@Profile("!test")  // 테스트 프로필에선 stub 빈 (support/IntegrationTestStubsConfig) 이 대신 주입
public class OpenAiEmbeddingAdapter implements EmbeddingClient {

    private static final String EMBEDDINGS_PATH = "/embeddings";

    private final WebClient webClient;
    private final String model;

    public OpenAiEmbeddingAdapter(WebClient.Builder builder, OpenAiProperties props) {
        this.webClient = builder
                .baseUrl(props.embedding().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.model = props.embedding().model();
    }

    @Override
    @CircuitBreaker(name = "openai-embedding")
    @Retry(name = "openai-embedding")
    public float[] embed(String text) {
        OpenAiEmbeddingResponse response = webClient.post()
                .uri(EMBEDDINGS_PATH)
                .bodyValue(Map.of("model", model, "input", text))
                .retrieve()
                .bodyToMono(OpenAiEmbeddingResponse.class)
                .block();

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("OpenAI embedding response was empty");
        }
        float[] embedding = response.data().get(0).embedding();
        if (embedding == null || embedding.length == 0) {
            throw new IllegalStateException("OpenAI embedding vector was missing");
        }
        return embedding;
    }
}
