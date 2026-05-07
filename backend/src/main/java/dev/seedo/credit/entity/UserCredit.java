package dev.seedo.credit.entity;

import dev.seedo.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "user_credits")
public class UserCredit extends BaseEntity {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "balance", nullable = false)
    private long balance;

    protected UserCredit() {
    }

    public UserCredit(UUID userId) {
        this.userId = userId;
        this.balance = 0L;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getBalance() {
        return balance;
    }
}
