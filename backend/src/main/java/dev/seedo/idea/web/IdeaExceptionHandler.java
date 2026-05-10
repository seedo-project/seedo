package dev.seedo.idea.web;

import dev.seedo.credit.application.InsufficientCreditException;
import dev.seedo.idea.application.AlreadyPurchasedException;
import dev.seedo.idea.application.IdeaNotFoundException;
import dev.seedo.idea.application.IdeaNotPurchasableException;
import dev.seedo.idea.application.SelfPurchaseException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

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
 */
@ControllerAdvice(basePackages = "dev.seedo.idea.web")
public class IdeaExceptionHandler {

    @ExceptionHandler(IdeaNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNotFound(IdeaNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Idea Not Found", e.getMessage());
    }

    @ExceptionHandler(AlreadyPurchasedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleAlreadyPurchased(AlreadyPurchasedException e) {
        return problem(HttpStatus.CONFLICT, "Already Purchased", e.getMessage());
    }

    @ExceptionHandler({IdeaNotPurchasableException.class, SelfPurchaseException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleNotPurchasable(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, "Not Purchasable", e.getMessage());
    }

    @ExceptionHandler(InsufficientCreditException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleInsufficientCredit(InsufficientCreditException e) {
        return problem(HttpStatus.BAD_REQUEST, "Insufficient Credit", e.getMessage());
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
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
        if (isIdeaPurchaseUniqueViolation(e)) {
            ProblemDetail pd = problem(HttpStatus.CONFLICT, "Conflict", "duplicate purchase blocked at db");
            return pd;
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

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        pd.setDetail(detail);
        return pd;
    }
}
