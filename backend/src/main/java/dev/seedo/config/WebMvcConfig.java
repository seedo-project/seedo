package dev.seedo.config;

import dev.seedo.common.web.CurrentUserIdArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC 설정.
 *
 * <ul>
 *   <li>모든 {@link RestController} 의 경로 앞에 {@code /api/v1} prefix 를 자동 부착 — 컨트롤러 마다
 *       prefix 를 적지 않아도 되고, v2 마이그레이션 시 한 곳에서 분기 가능.</li>
 *   <li>{@code @CurrentUserId} 파라미터 resolver 등록.</li>
 * </ul>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserIdArgumentResolver currentUserIdResolver;

    public WebMvcConfig(CurrentUserIdArgumentResolver currentUserIdResolver) {
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v1", clazz ->
                clazz.isAnnotationPresent(RestController.class)
                        && !clazz.getPackageName().startsWith("org.springdoc"));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdResolver);
    }
}
