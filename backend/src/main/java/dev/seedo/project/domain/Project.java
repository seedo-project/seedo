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

    // 채택 시점엔 비어 있다가 LEADER 가 직접 채우는 4 항목 (#140). publish 시 title / description 은 필수,
    // cover_image_url / guide_md 는 선택. V15 의 chk_projects_published_fields 가 DB 단 최후 가드.
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "guide_md")
    private String guideMd;

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

    /**
     * LEADER 가 작성한 4 항목을 부분 수정 — null 인 필드는 변경하지 않는다 (#140).
     *
     * <p>상태 가드는 호출자 (service) 가 검증한다 — 도메인은 단순히 값만 갱신. ARCHIVED / DELETED 인
     * 프로젝트 표지가 바뀌면 안 되는 정책 결정은 application 레이어에서.
     */
    public void updateIntro(String coverImageUrl, String title, String description, String guideMd) {
        if (coverImageUrl != null) {
            this.coverImageUrl = coverImageUrl;
        }
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (guideMd != null) {
            this.guideMd = guideMd;
        }
    }

    /**
     * 명시적 공개 — DRAFT → IN_PROGRESS (#140).
     *
     * <p>모집 흐름을 별도로 두지 않으므로 RECRUITING 을 건너뛴다 (#140 의 정책 결정). title 과 description
     * 이 채워져 있어야 하며 — V15 의 chk_projects_published_fields 가 DB 단 최후 가드.
     *
     * <p>cover_image_url / guide_md 는 선택 — 비어 있어도 공개 가능.
     */
    public void publish() {
        if (status != ProjectStatus.DRAFT) {
            throw new IllegalStateException("publish requires DRAFT, was " + status);
        }
        if (title == null || title.isBlank() || description == null || description.isBlank()) {
            throw new IllegalStateException("publish requires title and description to be set");
        }
        this.status = ProjectStatus.IN_PROGRESS;
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

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getGuideMd() {
        return guideMd;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
