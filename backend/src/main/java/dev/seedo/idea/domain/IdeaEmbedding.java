package dev.seedo.idea.domain;

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

/**
 * 검색용 임베딩. ideas 와 1:1 (PK = idea_id, FK CASCADE).
 *
 * <p>{@code embedding vector(1536)} 컬럼은 pgvector 가 정의하는 비표준 타입 — Hibernate 6.6 의
 * 기본 타입 매퍼에는 없다. MVP 에선 도메인 코드가 본문 임베딩을 자바에서 직접 다루지 않으므로
 * 본문 컬럼 매핑은 일부러 두지 않는다. 검색·RAG 작업 (다음 PR) 에서 native query 또는
 * {@code hibernate-vector} 모듈 추가로 매핑한다.
 *
 * <p>이 엔티티는 keywords 조회/갱신과 metadata (idea_id, created/updated_at) 만 담당한다.
 *
 * <p>PK 가 비-IDENTITY (idea_id 외부 주입) 이므로 Persistable 로 INSERT 분기를 명시한다.
 */
@Entity
@Table(name = "idea_embeddings")
public class IdeaEmbedding extends BaseEntity implements Persistable<Long> {

    @Id
    @Column(name = "idea_id", nullable = false, updatable = false)
    private Long ideaId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "keywords", nullable = false)
    private String[] keywords;

    @Transient
    private boolean isNew = true;

    protected IdeaEmbedding() {
    }

    public IdeaEmbedding(Long ideaId, String[] keywords) {
        this.ideaId = ideaId;
        this.keywords = keywords;
    }

    public void updateKeywords(String[] keywords) {
        this.keywords = keywords;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public Long getId() {
        return ideaId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Long getIdeaId() {
        return ideaId;
    }

    public String[] getKeywords() {
        return keywords;
    }
}
