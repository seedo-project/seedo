package dev.seedo.auth;

import dev.seedo.auth.application.AuthorityLoader;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1 시드 (USER role = 10 권한, ADMIN role = 14 권한) 기준으로 AuthorityLoader 가
 * 정확히 PERM_ 프리픽스 + 코드를 부여하는지 검증.
 */
@Transactional
class AuthorityLoaderIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AuthorityLoader loader;

    @PersistenceContext
    private EntityManager em;

    @Test
    void user_role_grants_ten_permissions_with_perm_prefix() {
        UUID uid = UserFixture.createWithRole(userRepo, em, 1L); // USER role

        Collection<GrantedAuthority> authorities = loader.loadFor(uid);

        assertThat(authorities).hasSize(10);
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .allMatch(a -> a.startsWith("PERM_"))
                .contains(
                        "PERM_IDEA_CREATE", "PERM_IDEA_PUBLISH", "PERM_IDEA_ARCHIVE", "PERM_IDEA_PURCHASE",
                        "PERM_PROJECT_CREATE", "PERM_PROJECT_FOLLOW",
                        "PERM_POST_CREATE", "PERM_POST_APPLY",
                        "PERM_COMMENT_CREATE", "PERM_HYPE_TOGGLE");
    }

    @Test
    void admin_role_grants_all_fourteen_permissions() {
        UUID uid = UserFixture.createWithRole(userRepo, em, 2L); // ADMIN role

        Collection<GrantedAuthority> authorities = loader.loadFor(uid);

        assertThat(authorities).hasSize(14);
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("PERM_CREDIT_REFUND", "PERM_CREDIT_ADJUST", "PERM_USER_SUSPEND",
                          "PERM_IDEA_HARD_DELETE");
    }

    @Test
    void user_without_role_returns_empty() {
        UUID uid = UserFixture.create(userRepo);

        assertThat(loader.loadFor(uid)).isEmpty();
    }

    @Test
    void unknown_user_returns_empty() {
        // 우리 users 테이블에 없는 UUID — JWT 는 검증됐지만 동기화 안 된 케이스
        assertThat(loader.loadFor(UUID.randomUUID())).isEmpty();
    }

}
