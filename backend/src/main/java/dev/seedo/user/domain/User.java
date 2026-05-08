package dev.seedo.user.domain;

import dev.seedo.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Supabase auth.users.id 와 동일한 UUID 를 외부에서 명시적으로 주입한다 (V1 마이그레이션 §6.3).
 * UUID PK 라 Persistable 을 구현해 SimpleJpaRepository.save() 가 merge (= SELECT 후 INSERT) 대신
 * persist 를 타게 한다.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity implements Persistable<UUID> {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "nickname", nullable = false, unique = true, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Transient
    private boolean isNew = true;

    protected User() {
    }

    public User(UUID id, String email, String nickname) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.status = UserStatus.ACTIVE;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void softDelete() {
        this.status = UserStatus.DELETED;
        this.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public UserStatus getStatus() {
        return status;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
