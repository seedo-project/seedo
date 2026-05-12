package dev.seedo.support;

import dev.seedo.idea.application.port.out.ChatClient;
import dev.seedo.idea.application.port.out.EmbeddingClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 모든 IT 에서 공통 사용하는 외부 통합 stub 빈. 실제 OpenAI / PG / 이메일 호출 안 가게 차단.
 *
 * <p>{@code OpenAi*Adapter} 들은 {@code @Profile("!test")} 로 비활성화돼 있어 테스트 컨텍스트엔
 * 구현체가 없다 — 이 빈들이 그 자리를 채운다. 특정 IT 가 호출 검증을 원하면 {@code @MockitoBean ...}
 * 으로 덮어쓰면 된다 (빈이 한 개라 이름 충돌 없음).
 *
 * <p>{@code AbstractIntegrationTest} 가 이 클래스를 @Import 한다.
 */
@TestConfiguration
public class IntegrationTestStubsConfig {

    /** 1536 차원 (pgvector 컬럼 정의와 동일) noop 벡터. 차원만 맞으면 INSERT 통과. */
    @Bean
    public EmbeddingClient stubEmbeddingClient() {
        return text -> new float[1536];
    }

    /** 결정적 stub — 마지막 USER 메시지를 그대로 echo. 특정 IT 가 더 정교한 응답이 필요하면 mock 으로 override. */
    @Bean
    public ChatClient stubChatClient() {
        return turns -> "stub assistant reply";
    }
}
