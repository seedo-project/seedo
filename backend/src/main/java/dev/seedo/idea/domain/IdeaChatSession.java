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
 * 챗봇 finalize 까지의 한 세션. 상태 전이는 도메인 메서드를 통해서만 — DB CHECK 가
 * status ↔ idea_id / finalized_at / abandoned_at 양방향 정합성을 강제한다 (V2 마이그레이션).
 */
@Entity
@Table(name = "idea_chat_sessions")
public class IdeaChatSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "idea_id")
    private Long ideaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChatSessionStatus status;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    @Column(name = "abandoned_at")
    private OffsetDateTime abandonedAt;

    protected IdeaChatSession() {
    }

    public IdeaChatSession(UUID userId) {
        this.userId = userId;
        this.status = ChatSessionStatus.IN_PROGRESS;
    }

    /** finalize 트랜잭션 끝 — idea 가 만들어진 후 호출 (CLAUDE.md §8.4). */
    public void finalize(Long ideaId) {
        this.ideaId = ideaId;
        this.status = ChatSessionStatus.FINALIZED;
        this.finalizedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void abandon() {
        this.status = ChatSessionStatus.ABANDONED;
        this.abandonedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getIdeaId() {
        return ideaId;
    }

    public ChatSessionStatus getStatus() {
        return status;
    }

    public OffsetDateTime getFinalizedAt() {
        return finalizedAt;
    }

    public OffsetDateTime getAbandonedAt() {
        return abandonedAt;
    }
}
