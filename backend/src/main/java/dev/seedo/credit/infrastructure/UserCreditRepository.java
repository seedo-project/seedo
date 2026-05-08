package dev.seedo.credit.infrastructure;

import dev.seedo.credit.domain.UserCredit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface UserCreditRepository extends JpaRepository<UserCredit, UUID> {

    /**
     * 잔액 변경 흐름의 표준 진입점. 같은 트랜잭션 안에서 SELECT FOR UPDATE 로 행 락을 잡고
     * {@link UserCredit#applyDelta(long)} + CreditTransaction INSERT 까지 한 번에 처리한다
     * (CLAUDE.md §6.2, §8.1, §8.2).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserCredit> findByUserId(UUID userId);
}
