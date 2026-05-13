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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Supabase auth.users.id 와 동일한 UUID 를 외부에서 명시적으로 주입한다 (V1 마이그레이션 §6.3).
 * UUID PK 라 Persistable 을 구현해 SimpleJpaRepository.save() 가 merge (= SELECT 후 INSERT) 대신
 * persist 를 타게 한다.
 *
 * <p>프로필 메타 (real_name / birth_date / gender / profile_image_url) 는 회원가입 시 metadata 로 들어와
 * V10 의 handle_new_user 트리거가 자동 채운다 — 또는 사용자가 마이페이지에서 나중에 수정. 모두 NULLABLE.
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

    @Column(name = "real_name", length = 50)
    private String realName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

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

    /**
     * 본인 프로필 메타 부분 갱신. 모든 인자가 NULL 가능 — caller 가 빈 값과 명시적 지움을 구분해 보낸다.
     * 도메인 검증은 V9 의 CHECK / NULLABLE 컬럼 정의와 일치하는 범위만 — 길이 / enum 매칭 정도.
     * 본문 비어있는 문자열은 NULL 로 정규화 (DB CHECK 와 정합).
     */
    public void updateProfile(String realName, LocalDate birthDate, Gender gender, String profileImageUrl) {
        this.realName = blankToNull(realName);
        if (this.realName != null && this.realName.length() > 50) {
            throw new IllegalArgumentException(
                    "realName length must be <= 50, was " + this.realName.length());
        }
        this.birthDate = birthDate;
        this.gender = gender;
        this.profileImageUrl = blankToNull(profileImageUrl);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
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

    public String getRealName() {
        return realName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
