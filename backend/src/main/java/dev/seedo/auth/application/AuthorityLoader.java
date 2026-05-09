package dev.seedo.auth.application;

import dev.seedo.config.CacheConfig;
import jakarta.persistence.EntityManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * users → user_roles → role_permissions → permissions 조인으로 사용자의 권한 목록을 로드한다.
 * 각 코드 앞에 "PERM_" 프리픽스를 붙여 {@link SimpleGrantedAuthority} 로 감싼다 (CLAUDE.md §9).
 *
 * <p>Caffeine TTL 5분 캐싱 — 매 요청마다 DB 히트 방지. 권한 변경 후 최대 5분 stale 가능.
 *
 * <p>users 테이블에 없는 userId 가 들어오면 빈 list 반환 — 보호된 엔드포인트는 자연스럽게 403.
 */
@Service
public class AuthorityLoader {

    private static final String PERMISSION_QUERY = """
            SELECT DISTINCT p.code FROM permissions p
            JOIN role_permissions rp ON rp.permission_id = p.id
            JOIN user_roles ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = CAST(?1 AS uuid)
            """;

    private final EntityManager em;

    public AuthorityLoader(EntityManager em) {
        this.em = em;
    }

    @Cacheable(CacheConfig.USER_AUTHORITIES_CACHE)
    public Collection<GrantedAuthority> loadFor(UUID userId) {
        @SuppressWarnings("unchecked")
        List<String> codes = em.createNativeQuery(PERMISSION_QUERY)
                .setParameter(1, userId.toString())
                .getResultList();
        return codes.stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority("PERM_" + code))
                .toList();
    }
}
