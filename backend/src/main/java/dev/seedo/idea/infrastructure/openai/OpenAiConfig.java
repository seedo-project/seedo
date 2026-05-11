package dev.seedo.idea.infrastructure.openai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@link OpenAiProperties} 활성화 + Webflux 자동 구성된 {@code WebClient.Builder} 빈 사용.
 * 추가 WebClient 커스터마이저 (커넥션 풀, 타임아웃) 가 필요해지면 여기서 빈 정의.
 */
@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfig {
}
