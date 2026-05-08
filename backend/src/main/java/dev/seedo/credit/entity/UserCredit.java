package dev.seedo.credit.entity;

import dev.seedo.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * 잔액 캐시. credit_transactions 합계의 캐시 역할이며 DB 트리거로 자동 갱신되지 않는다.
 * 변경은 반드시 다음 순서로 진행한다 (CLAUDE.md §6.2, §8):
 * <pre>
 *   1) {@link dev.seedo.credit.repository.UserCreditRepository#findByUserId} 로 SELECT FOR UPDATE
 *   2) {@link #applyDelta(long)} 로 메모리상 잔액 갱신
 *   3) 같은 @Transactional 안에서 CreditTransaction INSERT (balance_after 동봉)
 * </pre>
 * UUID PK 라 Persistable 을 구현해 save() 가 merge 대신 persist 를 타게 한다.
 */
@Entity
@Table(name = "user_credits")
public class UserCredit extends BaseEntity implements Persistable<UUID> {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Transient
    private boolean isNew = true;

    protected UserCredit() {
    }

    public UserCredit(UUID userId) {
        this.userId = userId;
        this.balance = 0L;
    }

    /**
     * 잔액에 delta 를 더한다. 결과가 음수면 즉시 실패 (DB CHECK 와 이중 방어).
     * 호출 전 반드시 SELECT FOR UPDATE 로 행 락이 걸려 있어야 한다.
     */
    public void applyDelta(long delta) {
        long next = this.balance + delta;
        if (next < 0L) {
            throw new IllegalStateException("잔액은 음수가 될 수 없습니다: " + next);
        }
        this.balance = next;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getBalance() {
        return balance;
    }
}
