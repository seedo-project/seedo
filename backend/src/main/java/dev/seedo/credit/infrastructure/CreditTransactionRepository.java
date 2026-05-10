package dev.seedo.credit.infrastructure;

import dev.seedo.credit.domain.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    /**
     * 멱등성 키 조회. (referenceType, referenceId) 가 둘 다 채워진 row 만 매칭한다 (V1 partial UNIQUE 와 동일 조건).
     * 같은 키로 들어온 두 번째 호출이 첫 호출의 트랜잭션 row 를 발견하면 service 는 idempotent skip 처리한다 (CLAUDE.md §6.7, §8.1).
     */
    Optional<CreditTransaction> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);
}
