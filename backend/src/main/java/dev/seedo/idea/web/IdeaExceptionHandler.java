package dev.seedo.idea.web;

import dev.seedo.common.web.ApiResponse;
import dev.seedo.credit.application.InsufficientCreditException;
import dev.seedo.idea.application.AlreadyPurchasedException;
import dev.seedo.idea.application.ChatMessageEmptyException;
import dev.seedo.idea.application.ChatSessionAccessDeniedException;
import dev.seedo.idea.application.ChatSessionNotAcceptingMessagesException;
import dev.seedo.idea.application.ChatSessionNotFinalizableException;
import dev.seedo.idea.application.ChatSessionNotFoundException;
import dev.seedo.idea.application.EmptyChatHistoryException;
import dev.seedo.idea.application.IdeaAccessDeniedException;
import dev.seedo.idea.application.IdeaNotFoundException;
import dev.seedo.idea.application.IdeaNotPurchasableException;
import dev.seedo.idea.application.IdeaNotVersionableException;
import dev.seedo.idea.application.SearchQueryEmptyException;
import dev.seedo.idea.application.SelfPurchaseException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.sql.SQLException;

/**
 * 아이디어 관련 application 예외 → HTTP 매핑. application 패키지는 Spring Web 의존을 두지 않고,
 * 매핑은 이 web 레이어 한 곳에서 처리한다 (CLAUDE.md backend "web → application → domain ← infrastructure").
 *
 * <p>scope 은 {@code dev.seedo.idea.web} — 같은 프리픽스의 컨트롤러 (`IdeaPurchaseController`) 만 타깃.
 * 다른 도메인 (admin/credit) 까지 영향을 주지 않는다.
 *
 * <p>{@link InsufficientCreditException} 은 credit 모듈 예외지만 idea 구매 흐름에서만 사용자에게 노출되므로
 * 여기서 같이 매핑. 향후 SPEND 호출하는 다른 도메인이 생기면 별도 advice 또는 공통 advice 로 분리.
 *
 * <p>응답 형태는 {@link ApiResponse#error(String)} — 성공 응답과 동일한 봉투. 핸들러는 {@code ResponseEntity}
 * 로 본문 + 상태 코드를 같이 반환한다 (예외 핸들러 + {@code @ResponseStatus} 조합이 본문을 누락시키는
 * 케이스를 회피).
 */
@ControllerAdvice(basePackages = "dev.seedo.idea.web")
public class IdeaExceptionHandler {

    @ExceptionHandler({IdeaNotFoundException.class, ChatSessionNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AlreadyPurchasedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyPurchased(AlreadyPurchasedException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ChatSessionNotAcceptingMessagesException.class)
    public ResponseEntity<ApiResponse<Void>> handleSessionNotAccepting(ChatSessionNotAcceptingMessagesException e) {
        // FINALIZED / ABANDONED 세션은 더 이상 메시지 추가 불가 — 상태 충돌 = 409.
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({
            IdeaNotPurchasableException.class,
            SelfPurchaseException.class,
            ChatSessionNotFinalizableException.class,
            IdeaNotVersionableException.class,
            SearchQueryEmptyException.class,
            ChatMessageEmptyException.class,
            EmptyChatHistoryException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({ChatSessionAccessDeniedException.class, IdeaAccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(RuntimeException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(InsufficientCreditException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientCredit(InsufficientCreditException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * idea_purchases 에서 race-time 으로 service 사전 체크를 빠져나간 UNIQUE 위반만 409 로 매핑한다.
     * 다른 무결성 위반 (FK, NOT NULL, 다른 테이블의 UNIQUE 등) 은 진짜 버그 신호 — 5xx 로 노출해야 한다.
     *
     * <p>매칭 기준: SQLState 23505 (unique violation) + 메시지에 "idea_purchases" 포함.
     * idea_purchases 는 두 UNIQUE 제약이 있다 — (idea_id, buyer_id) 와 transaction_id — 둘 다 동일 race
     * 시나리오에서 발생하므로 같은 409 매핑.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        if (isIdeaPurchaseUniqueViolation(e)) {
            // AlreadyPurchasedException 경로와 동일 메시지 — 클라이언트는 race 경로인지 사전 체크 경로인지
            // 구분할 필요 없이 같은 시나리오로 처리.
            return error(HttpStatus.CONFLICT, "already purchased");
        }
        // 그 외 무결성 위반은 다시 던져 Spring 기본 500 매핑 + 원본 스택트레이스 보존.
        throw e;
    }

    private boolean isIdeaPurchaseUniqueViolation(DataIntegrityViolationException e) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        if (!(root instanceof SQLException sqlEx)) {
            return false;
        }
        if (!"23505".equals(sqlEx.getSQLState())) {
            return false;
        }
        String msg = sqlEx.getMessage();
        return msg != null && msg.contains("idea_purchases");
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message));
    }
}
