package dev.seedo.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 REST 응답이 담기는 표준 봉투. 컨트롤러는 데이터 (T) 만 반환하고 {@link ApiResponseAdvice} 가
 * 자동으로 이 형태로 감싼다. 에러 응답은 {@code IdeaExceptionHandler} 등 advice 가 직접
 * {@link #error(String)} 를 반환한다.
 *
 * <p>JSON 예시:
 * <pre>
 *   { "status": "OK", "data": { ... } }
 *   { "status": "ERROR", "message": "already purchased: ..." }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String status,
        String message,
        T data
) {

    private static final String OK = "OK";
    private static final String ERROR = "ERROR";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(OK, null, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(OK, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ERROR, message, null);
    }
}
