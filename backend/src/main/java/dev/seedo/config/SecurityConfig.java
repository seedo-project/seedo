package dev.seedo.config;

import dev.seedo.auth.application.SupabaseAuthoritiesConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Supabase JWT 검증 + RBAC 권한 부여. JWKS / issuer 는 application.yml 의 spring.security.oauth2.* 설정으로
 * 자동 와이어드 (Spring Boot 의 oauth2-resource-server autoconfig 가 처리).
 *
 * <p>우리는 변환기 (Jwt → Authentication) 만 커스텀 — sub claim 으로 우리 DB 의 권한 로드.
 * 동일 JWT 가 Next.js (anon 키) / Spring (service_role) 양쪽에서 통한다 (CLAUDE.md §4.5).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Swagger UI + OpenAPI 스펙 경로는 인증 우회 — 개발자/리뷰어가 토큰 없이 API 목록을
                        // 둘러볼 수 있어야 한다. 실제 API 호출 시점엔 Authorize 버튼으로 JWT 입력 필요.
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(SupabaseAuthoritiesConverter authoritiesConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
