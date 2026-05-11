package dev.seedo.project.domain;

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
 * 프로젝트 aggregate root. 채택 트랜잭션 안에서 만들어지고 (CLAUDE.md §8.3), 이후 상태 전이
 * (RECRUITING/IN_PROGRESS/COMPLETED/ARCHIVED/DELETED) 는 도메인 메서드만 통해 일어난다 — V4 의 CHECK 가 2 차 방어.
 *
 * <p>{@code idea_snapshot_md} 는 채택 시점 {@code idea_documents.content_md} 의 박제본. 이후 아이디어가 새 버전을
 * 쌓아도 프로젝트의 출발 문서는 변하지 않는다 (§6.5 산 시점 스냅샷과 같은 사상).
 */
@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "idea_id", nullable = false, updatable = false)
    private Long ideaId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "leader_id", nullable = false, updatable = false)
    private UUID leaderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "idea_snapshot_md", nullable = false, updatable = false)
    private String ideaSnapshotMd;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected Project() {
    }

    private Project(Long ideaId, UUID leaderId, String ideaSnapshotMd) {
        this.ideaId = ideaId;
        this.leaderId = leaderId;
        this.ideaSnapshotMd = ideaSnapshotMd;
        this.status = ProjectStatus.DRAFT;
    }

    /** 채택 트랜잭션의 진입점 — DRAFT 상태로 시작 (CLAUDE.md §8.3). */
    public static Project create(Long ideaId, UUID leaderId, String ideaSnapshotMd) {
        return new Project(ideaId, leaderId, ideaSnapshotMd);
    }

    /** 모집 시작. 리더가 별 API 로 호출 — 이번 PR 범위 밖. */
    public void recruit() {
        if (status != ProjectStatus.DRAFT) {
            throw new IllegalStateException("recruit requires DRAFT, was " + status);
        }
        this.status = ProjectStatus.RECRUITING;
    }

    public void start() {
        if (status != ProjectStatus.RECRUITING) {
            throw new IllegalStateException("start requires RECRUITING, was " + status);
        }
        this.status = ProjectStatus.IN_PROGRESS;
    }

    public void complete() {
        if (status != ProjectStatus.IN_PROGRESS) {
            throw new IllegalStateException("complete requires IN_PROGRESS, was " + status);
        }
        this.status = ProjectStatus.COMPLETED;
    }

    public void archive() {
        if (status == ProjectStatus.ARCHIVED || status == ProjectStatus.DELETED) {
            throw new IllegalStateException("archive requires non-terminal, was " + status);
        }
        this.status = ProjectStatus.ARCHIVED;
    }

    /** 소프트삭제 — V4 의 CHECK ((status='DELETED') = (deleted_at IS NOT NULL)) 와 짝. */
    public void softDelete() {
        if (status == ProjectStatus.DELETED) {
            throw new IllegalStateException("already DELETED");
        }
        this.status = ProjectStatus.DELETED;
        this.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getIdeaId() {
        return ideaId;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public String getIdeaSnapshotMd() {
        return ideaSnapshotMd;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
