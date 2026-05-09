package dev.seedo.idea.domain;

import dev.seedo.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 아이디어 aggregate root. status 전이는 도메인 메서드만 통해서 일어나야 한다 (DB CHECK 가 2차 방어).
 * current_version_id 는 finalize / 새 버전 발행 트랜잭션에서 갱신된다 (CLAUDE.md §8.4).
 */
@Entity
@Table(name = "ideas")
public class Idea extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdeaStatus status;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "price_credits", nullable = false)
    private int priceCredits;

    @Column(name = "reward_credits", nullable = false)
    private int rewardCredits;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected Idea() {
    }

    public Idea(UUID authorId, int priceCredits, int rewardCredits) {
        this.authorId = authorId;
        this.priceCredits = priceCredits;
        this.rewardCredits = rewardCredits;
        this.status = IdeaStatus.DRAFT;
    }

    public void publish() {
        this.status = IdeaStatus.PUBLISHED;
    }

    public void archive() {
        this.status = IdeaStatus.ARCHIVED;
    }

    /** 소프트삭제 — DB CHECK ((status='DELETED') = (deleted_at IS NOT NULL)) 와 짝을 이룬다. */
    public void softDelete() {
        this.status = IdeaStatus.DELETED;
        this.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** finalize 또는 새 버전 발행 직후 호출 (§8.4). */
    public void updateCurrentVersion(Long documentId) {
        this.currentVersionId = documentId;
    }

    public Long getId() {
        return id;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public IdeaStatus getStatus() {
        return status;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public int getPriceCredits() {
        return priceCredits;
    }

    public int getRewardCredits() {
        return rewardCredits;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
