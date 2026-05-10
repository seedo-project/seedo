package dev.seedo.auth;

import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.user.domain.User;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityFilterChain 와이어링이 실제로 우리 SupabaseAuthoritiesConverter 를 거쳐
 * DB 에서 권한을 로드해 @PreAuthorize 까지 흘려보내는지 검증.
 *
 * <p>JwtDecoder 만 모킹 — 실제 JWKS 호출은 우회하고, 그 밖의 필터/컨버터/메서드 보안은 진짜.
 */
@AutoConfigureMockMvc
@Import(SecurityFilterChainIT.TestEndpoints.class)
@Transactional
class SecurityFilterChainIT extends AbstractIntegrationTest {

    private static final String BEARER = "Bearer test-token";

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void no_authorization_header_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/__test/any"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void user_role_jwt_passes_user_perm_endpoint() throws Exception {
        UUID uid = newUserWithRole(1L); // USER
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(uid));

        mockMvc.perform(get("/api/v1/__test/user-perm").header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    void user_role_jwt_blocked_from_admin_endpoint() throws Exception {
        UUID uid = newUserWithRole(1L); // USER 는 PERM_USER_SUSPEND 없음
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(uid));

        mockMvc.perform(get("/api/v1/__test/admin-only").header("Authorization", BEARER))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_role_jwt_passes_admin_endpoint() throws Exception {
        UUID uid = newUserWithRole(2L); // ADMIN
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(uid));

        mockMvc.perform(get("/api/v1/__test/admin-only").header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    void unknown_user_jwt_blocked_by_perm_check() throws Exception {
        // JWT 는 검증되지만 우리 users 테이블에 없는 sub → 권한 빈 list → 403
        UUID uid = UUID.randomUUID();
        when(jwtDecoder.decode(eq("test-token"))).thenReturn(jwtFor(uid));

        mockMvc.perform(get("/api/v1/__test/user-perm").header("Authorization", BEARER))
                .andExpect(status().isForbidden());
    }

    private Jwt jwtFor(UUID sub) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(sub.toString())
                .claim("iss", "https://test/auth/v1")
                .build();
    }

    private UUID newUserWithRole(long roleId) {
        UUID uid = UUID.randomUUID();
        userRepo.saveAndFlush(new User(uid, "u-" + uid + "@test", "n-" + uid.toString().substring(0, 8)));
        em.createNativeQuery(
                        "INSERT INTO user_roles(user_id, role_id) VALUES (CAST(:uid AS uuid), :rid)")
                .setParameter("uid", uid.toString())
                .setParameter("rid", roleId)
                .executeUpdate();
        em.flush();
        return uid;
    }

    @RestController
    @RequestMapping("/__test")
    static class TestEndpoints {
        @GetMapping("/any")
        String any() {
            return "ok";
        }

        @GetMapping("/user-perm")
        @PreAuthorize("hasAuthority('PERM_IDEA_CREATE')")
        String userPerm() {
            return "ok";
        }

        @GetMapping("/admin-only")
        @PreAuthorize("hasAuthority('PERM_USER_SUSPEND')")
        String adminOnly() {
            return "ok";
        }
    }
}
