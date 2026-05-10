package dev.seedo.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * {@link CurrentUserId} 어노테이션이 붙은 {@code UUID} 파라미터에 JWT 의 {@code sub} claim 을 주입한다.
 * SecurityFilterChain 이 통과시킨 요청만 controller 진입하므로 JWT 가 없는 케이스는 정상 흐름에서 발생하지 않는다.
 */
@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && UUID.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException(
                    "@CurrentUserId 사용 시 JwtAuthenticationToken 이 필요합니다 — SecurityFilterChain 설정 확인");
        }
        Jwt jwt = (Jwt) jwtAuth.getPrincipal();
        String sub = jwt.getSubject();
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT sub claim 이 UUID 형식이 아닙니다: " + sub, e);
        }
    }
}
