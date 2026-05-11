package dev.seedo.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 컨트롤러 응답 본문을 {@link ApiResponse} 봉투로 자동 감싼다. 컨트롤러는 raw DTO 만 반환하면 된다.
 *
 * <p>예외 케이스:
 * <ul>
 *   <li>이미 {@code ApiResponse} 인 경우 — 더블 래핑 방지 (예: 예외 핸들러가 직접 {@link ApiResponse#error} 반환)</li>
 *   <li>{@code String} 반환 — `StringHttpMessageConverter` 가 사용되면 직렬화 실패하므로 미감싸기.
 *       (현재 컨트롤러에선 발생하지 않지만 방어용)</li>
 *   <li>JSON 이 아닌 응답 (파일 다운로드 등) — 미감싸기</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "dev.seedo")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> type = returnType.getParameterType();
        return !ApiResponse.class.isAssignableFrom(type) && !String.class.equals(type);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof ApiResponse<?>) {
            return body;
        }
        if (selectedContentType != null && !MediaType.APPLICATION_JSON.includes(selectedContentType)) {
            return body;
        }
        return ApiResponse.ok(body);
    }
}
