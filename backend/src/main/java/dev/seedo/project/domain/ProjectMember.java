package dev.seedo.project.domain;

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
 * 프로젝트 멤버십. 채택 트랜잭션이 LEADER 한 명을 만든다 (CLAUDE.md §8.3). MEMBER 는 별 PR.
 *
 * <p>탈퇴 시 row 를 삭제하지 않고 {@code left_at} 만 세팅 — V4 의 partial UNIQUE
 * {@code (project_id, user_id) WHERE left_at IS NULL} 가 활성 멤버십 유일성 보장하면서
 * 옛 멤버십 이력은 보존한다.
 */
@Entity
@Table(name = "project_members")
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10, updatable = false)
    private ProjectMemberRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    protected ProjectMember() {
    }

    private ProjectMember(Long projectId, UUID userId, ProjectMemberRole role) {
        this.projectId = projectId;
        this.userId = userId;
        this.role = role;
    }

    /** 채택 시점 리더 멤버십 생성. */
    public static ProjectMember leader(Long projectId, UUID userId) {
        return new ProjectMember(projectId, userId, ProjectMemberRole.LEADER);
    }

    public static ProjectMember member(Long projectId, UUID userId) {
        return new ProjectMember(projectId, userId, ProjectMemberRole.MEMBER);
    }

    public void leave() {
        if (leftAt != null) {
            throw new IllegalStateException("already left");
        }
        this.leftAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ProjectMemberRole getRole() {
        return role;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public OffsetDateTime getLeftAt() {
        return leftAt;
    }
}
