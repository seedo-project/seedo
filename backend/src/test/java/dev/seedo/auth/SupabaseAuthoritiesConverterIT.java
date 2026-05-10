package dev.seedo.auth;

import dev.seedo.auth.application.SupabaseAuthoritiesConverter;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jwt → 권한 컬렉션 변환. AuthorityLoader 와 합쳐 sub claim 의 UUID 로 DB 권한 로드.
 * sub 가 없거나 UUID 가 아니면 빈 컬렉션 — Spring 이 익명으로 인증 마치고 보호된 엔드포인트는 자동 403.
 */
@Transactional
class SupabaseAuthoritiesConverterIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private SupabaseAuthoritiesConverter converter;

    @PersistenceContext
    private EntityManager em;

    @Test
    void valid_sub_with_user_role_returns_authorities() {
        UUID uid = UserFixture.createWithRole(userRepo, em, 1L);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(uid.toString())
                .claim("role", "authenticated")
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        assertThat(authorities).hasSize(10);
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("PERM_IDEA_CREATE");
    }

    @Test
    void unknown_user_sub_returns_empty() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void non_uuid_sub_returns_empty() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("not-a-uuid")
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void null_sub_returns_empty() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("role", "authenticated")
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

}
