package dev.seedo.user;

import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.user.domain.Gender;
import dev.seedo.user.domain.User;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static dev.seedo.support.AbstractIntegrationTest.SQLSTATE_CHECK_VIOLATION;
import static dev.seedo.support.AbstractIntegrationTest.assertSqlState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V9 의 프로필 컬럼 (real_name / birth_date / gender / profile_image_url) 정합성:
 * - 모두 NULLABLE — 미입력 허용
 * - gender CHECK 가 enum 후보 외 값 차단 (NULL 은 허용)
 * - {@link User#updateProfile} 도메인 메서드가 정상 갱신
 */
@Transactional
class UserProfileFieldsInvariantIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void all_profile_fields_can_be_null() {
        UUID id = UUID.randomUUID();
        // 신규 가입자는 4 필드가 모두 NULL 인 것이 정상 (사용자가 단계적으로 채움).
        assertThatCode(() ->
                userRepo.saveAndFlush(new User(id, "u-" + id + "@test", nick(id)))
        ).doesNotThrowAnyException();

        User loaded = userRepo.findById(id).orElseThrow();
        assertThat(loaded.getRealName()).isNull();
        assertThat(loaded.getBirthDate()).isNull();
        assertThat(loaded.getGender()).isNull();
        assertThat(loaded.getProfileImageUrl()).isNull();
    }

    @Test
    void gender_outside_check_set_blocked() {
        UUID id = UUID.randomUUID();
        userRepo.saveAndFlush(new User(id, "u-" + id + "@test", nick(id)));

        assertThatThrownBy(() ->
                em.createNativeQuery(
                                "UPDATE users SET gender = 'UNKNOWN' WHERE id = CAST(:id AS uuid)")
                        .setParameter("id", id.toString())
                        .executeUpdate()
        ).satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void update_profile_persists_all_fields() {
        UUID id = UUID.randomUUID();
        User user = userRepo.saveAndFlush(new User(id, "u-" + id + "@test", nick(id)));

        user.updateProfile(
                "홍길동",
                LocalDate.of(1990, 3, 15),
                Gender.MALE,
                "https://cdn.example.com/p.jpg"
        );
        userRepo.saveAndFlush(user);

        User reloaded = userRepo.findById(id).orElseThrow();
        assertThat(reloaded.getRealName()).isEqualTo("홍길동");
        assertThat(reloaded.getBirthDate()).isEqualTo(LocalDate.of(1990, 3, 15));
        assertThat(reloaded.getGender()).isEqualTo(Gender.MALE);
        assertThat(reloaded.getProfileImageUrl()).isEqualTo("https://cdn.example.com/p.jpg");
    }

    @Test
    void update_profile_normalizes_blank_to_null() {
        UUID id = UUID.randomUUID();
        User user = userRepo.saveAndFlush(new User(id, "u-" + id + "@test", nick(id)));

        // 빈 문자열은 NULL 로 정규화 — DB CHECK 와 정합 + Supabase 직결 RLS UPDATE 와도 일관.
        user.updateProfile("   ", null, null, "");
        userRepo.saveAndFlush(user);

        User reloaded = userRepo.findById(id).orElseThrow();
        assertThat(reloaded.getRealName()).isNull();
        assertThat(reloaded.getProfileImageUrl()).isNull();
    }

    @Test
    void update_profile_rejects_overlong_real_name() {
        UUID id = UUID.randomUUID();
        User user = userRepo.saveAndFlush(new User(id, "u-" + id + "@test", nick(id)));

        String over51 = "가".repeat(51);
        assertThatThrownBy(() -> user.updateProfile(over51, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("realName");
    }

    private static String nick(UUID id) {
        return "n-" + id.toString().substring(0, 8);
    }
}
