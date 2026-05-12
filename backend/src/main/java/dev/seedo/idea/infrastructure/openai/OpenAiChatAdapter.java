package dev.seedo.idea.infrastructure.openai;

import dev.seedo.idea.application.port.out.ChatClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI {@code gpt-4o-mini} Chat Completions 어댑터 (CLAUDE.md §11). WebClient + Resilience4j 서킷/재시도 보호.
 *
 * <p>이 어댑터는 {@link ChatClient} 의 유일한 구현체. 도메인 / application 은 인터페이스만 알고,
 * OpenAI 구체 사항 (URL, 모델명, DTO) 은 이 패키지에 격리.
 *
 * <p>임베딩과 달리 챗봇 호출 실패는 사용자에게 직접 노출 — 5xx 또는 매핑된 4xx 로 응답. 임베딩처럼 swallow
 * 하지 않는다 (lessons H.1 / 챗봇은 부가 기능 아님).
 */
@Component
@Profile("!test")  // 테스트 프로필에선 stub 빈 (support/IntegrationTestStubsConfig) 이 대신 주입
public class OpenAiChatAdapter implements ChatClient {

    private static final String CHAT_PATH = "/chat/completions";

    private final WebClient webClient;
    private final String model;

    public OpenAiChatAdapter(WebClient.Builder builder, OpenAiProperties props) {
        // Reactor Netty HttpClient 에 responseTimeout 을 박아야 application.yml 의 openai.chat.timeout 이
        // 실제로 작동한다 (lessons H.1). 안 박으면 외부 응답이 늦어도 .block() 이 무한 대기 + 재시도/서킷 무력.
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(props.chat().timeout());
        this.webClient = builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(props.chat().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.model = props.chat().model();
    }

    @Override
    @CircuitBreaker(name = "openai-chat")
    @Retry(name = "openai-chat")
    public String complete(List<ChatTurn> turns) {
        List<Map<String, String>> messages = turns.stream()
                .map(t -> Map.of(
                        // OpenAI 표준 role 표기는 소문자 — user/assistant/system. enum.name() 을 소문자화해서 일치.
                        "role", t.role().name().toLowerCase(),
                        "content", t.content()
                ))
                .toList();

        OpenAiChatResponse response = webClient.post()
                .uri(CHAT_PATH)
                .bodyValue(Map.of("model", model, "messages", messages))
                .retrieve()
                .bodyToMono(OpenAiChatResponse.class)
                .block();

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI chat response was empty");
        }
        OpenAiChatResponse.Message message = response.choices().get(0).message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            throw new IllegalStateException("OpenAI chat response had empty content");
        }
        return message.content();
    }
}
