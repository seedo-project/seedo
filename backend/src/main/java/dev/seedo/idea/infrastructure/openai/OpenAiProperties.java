package dev.seedo.idea.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.yml 의 {@code openai.*} 바인딩. embedding / chat 두 호출 채널이 같은 API 키를 공유하지만
 * 모델 / 타임아웃은 분리 가능하도록 별도 그룹.
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        Embedding embedding,
        Chat chat
) {

    public record Embedding(
            String baseUrl,
            String model,
            Duration timeout
    ) {
    }

    public record Chat(
            String baseUrl,
            String model,
            Duration timeout
    ) {
    }
}
