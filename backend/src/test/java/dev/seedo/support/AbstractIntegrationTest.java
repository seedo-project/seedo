package dev.seedo.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모든 통합테스트가 상속하는 베이스. 한 번만 띄운 PostgreSQL 16 컨테이너를 공유한다.
 * Flyway 가 컨테이너에 V1+ 를 자동 적용하므로 테스트 시작 시점에 스키마는 prod 와 동일.
 *
 * 이미지: {@code pgvector/pgvector:pg16} — V2 부터 vector 확장 사용.
 * 표준 postgres 이미지로 인식시키기 위해 {@code asCompatibleSubstituteFor("postgres")} 필요.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    /** PostgreSQL: check constraint 위반 (예: balance >= 0, type-부호 정합성 등) */
    public static final String SQLSTATE_CHECK_VIOLATION = "23514";

    /** PostgreSQL: unique 제약 위반 (예: 멱등성 키 중복) */
    public static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    /** PostgreSQL: foreign key 위반 (예: RBAC 매핑 RESTRICT) */
    public static final String SQLSTATE_FOREIGN_KEY_VIOLATION = "23503";

    /** PostgreSQL: PL/pgSQL RAISE EXCEPTION 기본 SQLSTATE (예: append-only 트리거) */
    public static final String SQLSTATE_RAISE_EXCEPTION = "P0001";

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * Cause chain 을 따라가 첫 SQLException 을 찾고 SQLState 를 검증한다.
     * 메시지 문구 의존 (locale/드라이버 버전 차이) 보다 안정적.
     */
    public static void assertSqlState(Throwable thrown, String expectedSqlState) {
        Throwable cursor = thrown;
        while (cursor != null) {
            if (cursor instanceof SQLException sqlEx) {
                assertThat(sqlEx.getSQLState())
                        .as("expected SQLState %s but got %s (message: %s)",
                                expectedSqlState, sqlEx.getSQLState(), sqlEx.getMessage())
                        .isEqualTo(expectedSqlState);
                return;
            }
            cursor = cursor.getCause();
        }
        throw new AssertionError("No SQLException in cause chain of: " + thrown, thrown);
    }
}
