package dev.seedo.project.web;

import dev.seedo.common.web.ApiResponse;
import dev.seedo.idea.application.IdeaNotFoundException;
import dev.seedo.project.application.AdoptionRequiresPurchaseException;
import dev.seedo.project.application.IdeaNotAdoptableException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.sql.SQLException;

/**
 * project 도메인 컨트롤러용 예외 매핑. scope 은 {@code dev.seedo.project.web} — IdeaExceptionHandler 와
 * 도메인별로 분리.
 *
 * <p>{@link IdeaNotFoundException} 은 idea 도메인 예외지만 채택 진입점에서도 4xx 로 노출되므로 여기서 매핑.
 * basePackages 가 달라 IdeaExceptionHandler 와 충돌하지 않는다.
 *
 * <p>응답은 {@link ApiResponse} 봉투 + {@code ResponseEntity} 로 상태 코드 직접 지정
 * (메모리 "ExceptionHandler 본문 누락 함정" — @ResponseStatus 조합 회피).
 */
@ControllerAdvice(basePackages = "dev.seedo.project.web")
public class ProjectExceptionHandler {

    @ExceptionHandler(IdeaNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdeaNotFound(IdeaNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({IdeaNotAdoptableException.class, AdoptionRequiresPurchaseException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * race-time 으로 service 사전 체크 (existsByIdeaIdAndRewardType) 를 빠져나간 두 번째 보상 시도가
     * V4 의 partial UNIQUE {@code rewards_adoption_idea_uniq} 에 걸린 경우만 409 로 매핑한다.
     *
     * <p>idea row 락 (PESSIMISTIC_WRITE) 덕분에 정상 흐름에서는 사전 체크가 잡아 5xx 로 노출될 일 없지만,
     * 락 가드가 어떤 이유로 우회됐을 때 마지막 방어선이 되어준다.
     *
     * <p>매칭 기준: SQLState 23505 + 메시지에 인덱스 이름 포함. 다른 무결성 위반은 다시 던져 Spring 기본 5xx 매핑.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        if (isAdoptionRewardUniqueViolation(e)) {
            return error(HttpStatus.CONFLICT, "reward already paid for this idea");
        }
        throw e;
    }

    private boolean isAdoptionRewardUniqueViolation(DataIntegrityViolationException e) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        if (!(root instanceof SQLException sqlEx)) {
            return false;
        }
        if (!"23505".equals(sqlEx.getSQLState())) {
            return false;
        }
        String msg = sqlEx.getMessage();
        return msg != null && msg.contains("rewards_adoption_idea_uniq");
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message));
    }
}
