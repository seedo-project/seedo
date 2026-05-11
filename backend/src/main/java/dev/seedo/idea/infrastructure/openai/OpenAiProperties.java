package dev.seedo.idea.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.yml 의 {@code openai.*} 바인딩.
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        Embedding embedding
) {

    public record Embedding(
            String baseUrl,
            String model,
            Duration timeout
    ) {
    }
}
