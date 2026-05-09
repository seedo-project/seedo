package dev.seedo.idea.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 아이디어 구매 기록. INSERT 만 있고 수정 없음 — 모든 컬럼 updatable=false.
 *
 * <p>document_id 는 산 시점 스냅샷 (§6.5) — 이후 새 버전이 발행돼도 구매자는 산 버전 유지.
 * transaction_id 는 credit_transactions 의 SPEND 행과 1:1 (UNIQUE on transaction_id, V2).
 *
 * <p>본인 구매(author_id == buyer_id) 는 V2 의 block_self_purchase 트리거가 차단.
 */
@Entity
@Table(name = "idea_purchases")
public class IdeaPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "idea_id", nullable = false, updatable = false)
    private Long ideaId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "buyer_id", nullable = false, updatable = false)
    private UUID buyerId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private Long documentId;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private Long transactionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected IdeaPurchase() {
    }

    public IdeaPurchase(Long ideaId, UUID buyerId, Long documentId, Long transactionId) {
        this.ideaId = ideaId;
        this.buyerId = buyerId;
        this.documentId = documentId;
        this.transactionId = transactionId;
    }

    public Long getId() {
        return id;
    }

    public Long getIdeaId() {
        return ideaId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
