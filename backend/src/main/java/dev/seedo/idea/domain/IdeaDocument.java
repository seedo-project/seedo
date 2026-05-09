package dev.seedo.idea.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 버전별 본문. 한 번 INSERT 후 절대 수정 안 함 — published 후 수정도 새 (idea_id, version+1) row (§6.5).
 * 모든 컬럼 updatable=false 로 컨벤션 강제. 분쟁 방지를 위해 idea_purchases.document_id 로 산 시점 스냅샷 보존.
 */
@Entity
@Table(name = "idea_documents")
public class IdeaDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "idea_id", nullable = false, updatable = false)
    private Long ideaId;

    @Column(name = "version", nullable = false, updatable = false)
    private int version;

    @Column(name = "title", nullable = false, updatable = false, length = 200)
    private String title;

    @Column(name = "content_md", nullable = false, updatable = false)
    private String contentMd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected IdeaDocument() {
    }

    public IdeaDocument(Long ideaId, int version, String title, String contentMd) {
        this.ideaId = ideaId;
        this.version = version;
        this.title = title;
        this.contentMd = contentMd;
    }

    public Long getId() {
        return id;
    }

    public Long getIdeaId() {
        return ideaId;
    }

    public int getVersion() {
        return version;
    }

    public String getTitle() {
        return title;
    }

    public String getContentMd() {
        return contentMd;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
