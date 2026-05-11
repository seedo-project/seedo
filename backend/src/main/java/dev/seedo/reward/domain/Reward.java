package dev.seedo.reward.domain;

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
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 보상 원장. ADOPTION 타입은 채택 트랜잭션 안에서 곧장 PAID 로 INSERT 된다 (MVP, CLAUDE.md §8.3).
 * 다른 타입(INTERVIEW/ADMIN/OTHER)은 추후 결재 흐름에서 PENDING 으로 생성됐다가 PAID 로 전이될 수 있다.
 *
 * <p>모든 컬럼 updatable=false — 보상 row 는 인서트 후 변하지 않는다. 정정이 필요하면 별 row.
 * V4 의 CHECK 가 (reward_type='ADOPTION') = (idea_id IS NOT NULL),
 * (status='PAID') = (transaction_id IS NOT NULL) 양방향 정합성 강제.
 */
@Entity
@Table(name = "rewards")
public class Reward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 20, updatable = false)
    private RewardType rewardType;

    @Column(name = "amount", nullable = false, updatable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    private RewardStatus status;

    @Column(name = "transaction_id", updatable = false)
    private Long transactionId;

    @Column(name = "idea_id", updatable = false)
    private Long ideaId;

    @Column(name = "paid_at", updatable = false)
    private OffsetDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Reward() {
    }

    private Reward(UUID recipient, RewardType type, int amount, RewardStatus status,
                   Long transactionId, Long ideaId, OffsetDateTime paidAt) {
        this.recipientUserId = recipient;
        this.rewardType = type;
        this.amount = amount;
        this.status = status;
        this.transactionId = transactionId;
        this.ideaId = ideaId;
        this.paidAt = paidAt;
    }

    /** 채택 시점 인스턴트 지급 — PENDING 단계 없이 PAID 로 시작 (MVP). */
    public static Reward adoptionPaid(UUID recipient, int amount, Long ideaId, Long transactionId) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative: " + amount);
        }
        return new Reward(recipient, RewardType.ADOPTION, amount, RewardStatus.PAID,
                transactionId, ideaId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public Long getId() {
        return id;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public RewardType getRewardType() {
        return rewardType;
    }

    public int getAmount() {
        return amount;
    }

    public RewardStatus getStatus() {
        return status;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Long getIdeaId() {
        return ideaId;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
