package dev.seedo.idea.infrastructure.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.seedo.idea.application.port.out.ChatClient;
import dev.seedo.idea.domain.ChatMessageRole;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    /**
     * finalize 전용 시스템 프롬프트. JSON mode 와 짝으로, 모델이 {@code {title, contentMd}} 만 반환하도록 가이드.
     * title 길이 상한은 V2 의 {@code idea_documents.title varchar(200)} 과 일치.
     */
    private static final String SYNTHESIZE_SYSTEM_PROMPT = """
            당신은 사용자와의 대화 내용을 한 편의 기획문서로 정리하는 도우미입니다.
            오직 다음 JSON 형식으로만 응답하세요:
            {"title": "<한 줄 제목, 최대 200자>", "contentMd": "<마크다운 본문>"}
            title 은 한 줄, 마크다운/따옴표 금지. contentMd 는 ## 헤더 / 불릿 / 단락으로 구성된 자연스러운 마크다운.
            대화에서 도출되지 않은 사실은 추가하지 마세요.
            """;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OpenAiChatAdapter(WebClient.Builder builder, OpenAiProperties props, ObjectMapper objectMapper) {
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
        this.objectMapper = objectMapper;
        this.model = props.chat().model();
    }

    @Override
    @CircuitBreaker(name = "openai-chat")
    @Retry(name = "openai-chat")
    public String complete(List<ChatTurn> turns) {
        OpenAiChatResponse response = postChat(turns, null);
        OpenAiChatResponse.Message message = firstMessage(response);
        if (message.content() == null || message.content().isBlank()) {
            throw new IllegalStateException("OpenAI chat response had empty content");
        }
        return message.content();
    }

    @Override
    @CircuitBreaker(name = "openai-chat")
    @Retry(name = "openai-chat")
    public IdeaDocumentDraft synthesizeIdeaDocument(List<ChatTurn> turns) {
        // caller 가 system 프롬프트를 안 넣어 보내도 어댑터가 finalize 전용 프롬프트를 첫 turn 에 자동 주입.
        List<ChatTurn> withSystemPrompt = new ArrayList<>(turns.size() + 1);
        withSystemPrompt.add(new ChatTurn(ChatMessageRole.SYSTEM, SYNTHESIZE_SYSTEM_PROMPT));
        withSystemPrompt.addAll(turns);

        OpenAiChatResponse response = postChat(withSystemPrompt, Map.of("type", "json_object"));
        OpenAiChatResponse.Message message = firstMessage(response);
        if (message.content() == null || message.content().isBlank()) {
            throw new IllegalStateException("OpenAI synthesize response had empty content");
        }
        return parseDraft(message.content());
    }

    private OpenAiChatResponse postChat(List<ChatTurn> turns, Map<String, String> responseFormat) {
        List<Map<String, String>> messages = turns.stream()
                .map(t -> Map.of(
                        // OpenAI 표준 role 표기는 소문자 — user/assistant/system. enum.name() 을 소문자화해서 일치.
                        // Locale.ROOT 명시 — 우리 enum 은 ASCII 라 터키 locale 함정에 안 걸리지만, future-proofing.
                        "role", t.role().name().toLowerCase(Locale.ROOT),
                        "content", t.content()
                ))
                .toList();

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
        }

        return webClient.post()
                .uri(CHAT_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OpenAiChatResponse.class)
                .block();
    }

    private OpenAiChatResponse.Message firstMessage(OpenAiChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI chat response was empty");
        }
        OpenAiChatResponse.Message message = response.choices().get(0).message();
        if (message == null) {
            throw new IllegalStateException("OpenAI chat response had no message");
        }
        return message;
    }

    private IdeaDocumentDraft parseDraft(String json) {
        // 예외 메시지에는 LLM 원문(json)을 절대 포함하지 않는다 — 사용자 대화 유래 텍스트가 운영 로그 /
        // 클라이언트 응답 본문(ApiResponse.error)으로 노출될 수 있다. 디버깅이 필요하면 응답 길이만 남긴다.
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "OpenAI synthesize response was not valid JSON (length=" + json.length() + ")", e);
        }
        String title = textOrNull(node.get("title"));
        String contentMd = textOrNull(node.get("contentMd"));
        if (title == null || title.isBlank() || contentMd == null || contentMd.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI synthesize response missing title/contentMd (length=" + json.length() + ")");
        }
        // 모델이 가끔 길이 초과를 만든다 — DB CHECK 도달 전 잘라낸다 (V2 idea_documents.title varchar(200)).
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }
        return new IdeaDocumentDraft(title, contentMd);
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
