package dev.seedo.config;

import dev.seedo.common.web.CurrentUserId;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi 를 통해 자동 생성되는 OpenAPI 스펙의 메타 정보 + 인증 스킴 설정.
 *
 * <p>Swagger UI 진입: {@code /swagger-ui/index.html}, raw 스펙: {@code /v3/api-docs}.
 * 두 경로 모두 {@code SecurityConfig} 에서 인증 우회로 화이트리스트.
 *
 * <p>JWT 인증: Swagger UI 상단의 "Authorize" 버튼을 통해 Supabase 가 발급한 JWT 를 입력하면
 * 모든 보호된 엔드포인트 호출에 {@code Authorization: Bearer <token>} 헤더가 자동 부착된다.
 * 같은 토큰이 프론트엔드 (Next.js + supabase-js) 에서도 통한다 (CLAUDE.md §4.5).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearer-jwt";

    static {
        // @CurrentUserId 파라미터는 JWT sub claim 으로 자동 주입되므로 OpenAPI 스펙에 노출하지 않는다.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }

    @Bean
    public OpenAPI seedoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Seedo Backend API")
                        .description("Seedo 의 Spring Boot REST API. 인증은 Supabase 발급 JWT — 상단 Authorize 버튼에서 입력.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Supabase 가 발급한 JWT 를 그대로 붙여넣으면 됨.")));
    }
}
