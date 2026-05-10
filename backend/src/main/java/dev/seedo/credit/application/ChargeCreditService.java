package dev.seedo.credit.application;

import dev.seedo.credit.domain.CreditTransaction;
import dev.seedo.credit.domain.UserCredit;
import dev.seedo.credit.infrastructure.CreditTransactionRepository;
import dev.seedo.credit.infrastructure.UserCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 크레딧 잔액 변경 코어. 잔액 캐시 ({@code user_credits}) 와 원장 ({@code credit_transactions}) 을
 * 한 트랜잭션에서 함께 갱신해 두 값이 절대 어긋나지 않도록 한다 (CLAUDE.md §6.2, §8.1).
 *
 * <p>호출 흐름:
 * <ol>
 *   <li>락-전 멱등성 체크 — 이미 처리된 (referenceType, referenceId) 면 즉시 idempotent skip</li>
 *   <li>{@code user_credits} 행 SELECT FOR UPDATE — 같은 사용자 동시 호출은 직렬화</li>
 *   <li>락-후 멱등성 재확인 — 1번 통과 후 다른 트랜잭션이 먼저 commit 한 경우 잡는다</li>
 *   <li>잔액에 delta 적용 ({@link UserCredit#applyDelta} → balance &lt; 0 이면 도메인 단계에서 throw)</li>
 *   <li>{@code credit_transactions} INSERT — balance_after 동봉</li>
 * </ol>
 *
 * <p>마지막 보호선: 위 모두 통과해도 (refType, refId) UNIQUE 인덱스가 race-time 중복 INSERT 를 차단한다.
 * 이 경우 caller 는 {@link org.springframework.dao.DataIntegrityViolationException} 을 받고 재시도하면
 * 다음 호출의 1번/3번 단계가 idempotent skip 으로 처리한다.
 */
@Service
public class ChargeCreditService {

    private final UserCreditRepository creditRepo;
    private final CreditTransactionRepository txRepo;

    public ChargeCreditService(UserCreditRepository creditRepo, CreditTransactionRepository txRepo) {
        this.creditRepo = creditRepo;
        this.txRepo = txRepo;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChargeResult charge(ChargeCommand cmd) {
        if (cmd.hasReference()) {
            Optional<CreditTransaction> existing = txRepo.findByReferenceTypeAndReferenceId(
                    cmd.referenceType(), cmd.referenceId());
            if (existing.isPresent()) {
                return idempotentResult(cmd, existing.get());
            }
        }

        UserCredit credit = creditRepo.findByUserId(cmd.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "user_credits row missing for userId=" + cmd.userId()
                                + " — V3 트리거가 가입 시 채우도록 되어 있다. 트리거가 안 돌았거나 테스트 셋업 누락."));

        if (cmd.hasReference()) {
            Optional<CreditTransaction> existing = txRepo.findByReferenceTypeAndReferenceId(
                    cmd.referenceType(), cmd.referenceId());
            if (existing.isPresent()) {
                return idempotentResult(cmd, existing.get());
            }
        }

        long balanceBefore = credit.getBalance();
        try {
            credit.applyDelta(cmd.amount());
        } catch (IllegalArgumentException e) {
            // CreditAmount.balance() 가 결과 잔액 < 0 이면 throw — 잔액 부족을 typed 예외로 승격.
            throw new InsufficientCreditException(cmd.userId(), balanceBefore, cmd.amount());
        }

        CreditTransaction tx = txRepo.saveAndFlush(CreditTransaction.of(
                cmd.userId(),
                cmd.amount(),
                cmd.type(),
                credit.getBalance(),
                cmd.referenceType(),
                cmd.referenceId(),
                cmd.description()
        ));

        return new ChargeResult(credit.getBalance(), tx.getId(), false);
    }

    private ChargeResult idempotentResult(ChargeCommand cmd, CreditTransaction existing) {
        long balance = creditRepo.findById(cmd.userId())
                .map(UserCredit::getBalance)
                .orElse(existing.getBalanceAfter());
        return new ChargeResult(balance, existing.getId(), true);
    }
}
