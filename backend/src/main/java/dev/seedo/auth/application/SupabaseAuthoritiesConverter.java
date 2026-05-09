package dev.seedo.auth.application;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 검증된 Jwt 의 {@code sub} claim (Supabase auth.users.id, UUID) 를 우리 users 테이블 ID 로 보고
 * {@link AuthorityLoader} 로 권한 목록을 가져와 Spring Security 의 GrantedAuthority 컬렉션을 만든다.
 *
 * <p>{@code sub} 가 UUID 형식이 아니면 빈 리스트 — Spring 이 받아 익명 권한으로 인증을 마치고,
 * @PreAuthorize 가 걸린 엔드포인트는 자동으로 403.
 */
@Component
public class SupabaseAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final AuthorityLoader loader;

    public SupabaseAuthoritiesConverter(AuthorityLoader loader) {
        this.loader = loader;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null) {
            return List.of();
        }
        UUID userId;
        try {
            userId = UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        return loader.loadFor(userId);
    }
}
