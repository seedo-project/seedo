package dev.seedo.project.application;

import java.util.UUID;

/**
 * 구매 이력 없는 외부 채택자가 채택 시도 (CLAUDE.md §8.3 step 2 정책: "산 사람만 채택").
 * 자가 채택자(작성자 본인) 는 이 검증을 건너뛴다 — 작성자는 §6.6 으로 본인 아이디어를 구매할 수 없기 때문.
 *
 * <p>HTTP 매핑(400)은 web 레이어.
 */
public class AdoptionRequiresPurchaseException extends RuntimeException {

    public AdoptionRequiresPurchaseException(Long ideaId, UUID adopterId) {
        super("adoption requires prior purchase: ideaId=" + ideaId + ", adopterId=" + adopterId);
    }
}
