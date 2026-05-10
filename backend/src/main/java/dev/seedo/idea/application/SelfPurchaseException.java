package dev.seedo.idea.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/**
 * 본인 아이디어를 본인이 구매하려는 시도 (CLAUDE.md §6.6). DB 의 block_self_purchase 트리거가 마지막 방어선이지만
 * service 단계에서 명시적으로 막아 4xx 로 변환한다.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class SelfPurchaseException extends RuntimeException {

    public SelfPurchaseException(Long ideaId, UUID buyerId) {
        super("cannot purchase own idea: ideaId=" + ideaId + ", buyerId=" + buyerId);
    }
}
