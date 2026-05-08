package dev.seedo.credit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * append-only 원장. UPDATE/DELETE 는 DB 트리거가 차단한다.
 * 변경이 필요하면 type=ADJUST 새 row 를 INSERT 한다.
 */
@Entity
@Table(name = "credit_transactions")
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private CreditType type;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private long balanceAfter;

    @Column(name = "reference_type", updatable = false, length = 50)
    private String referenceType;

    @Column(name = "reference_id", updatable = false, length = 100)
    private String referenceId;

    @Column(name = "description", updatable = false, length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected CreditTransaction() {
    }

    private CreditTransaction(UUID userId, long amount, CreditType type, long balanceAfter,
                              String referenceType, String referenceId, String description) {
        this.userId = userId;
        this.amount = amount;
        this.type = type;
        this.balanceAfter = balanceAfter;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.description = description;
    }

    public static CreditTransaction of(UUID userId, long amount, CreditType type, long balanceAfter,
                                       String referenceType, String referenceId) {
        return new CreditTransaction(userId, amount, type, balanceAfter, referenceType, referenceId, null);
    }

    public static CreditTransaction adjust(UUID userId, long amount, long balanceAfter, String description) {
        return new CreditTransaction(userId, amount, CreditType.ADJUST, balanceAfter, null, null, description);
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getAmount() {
        return amount;
    }

    public CreditType getType() {
        return type;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
