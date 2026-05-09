package dev.seedo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caffeine 캐시 매니저. 권한 로딩이 매 요청마다 DB 히트 안 하도록 user-authorities 캐시 사용 (CLAUDE.md §9).
 * TTL 5분 — 권한 변경 후 최대 5분 stale, MVP 수용.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USER_AUTHORITIES_CACHE = "user-authorities";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(USER_AUTHORITIES_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(10_000));
        return manager;
    }
}
