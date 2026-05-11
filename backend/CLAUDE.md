# backend/ — Spring Boot 작업 가이드

> 부트스트랩 완료 (Spring Boot 3.5.14, Gradle 8.14.4 wrapper, Flyway 10, Java 25 toolchain). 도메인 코드는 아직 0줄 — 첫 작업은 Flyway V1 (RBAC + 크레딧).

> 루트 `CLAUDE.md`(도메인·스키마·트랜잭션 규칙)와 함께 자동 로드된다. 이 파일은 **Spring/Java 구현 디테일**에만 집중.

---

## 스택 (확정)

- **Java 25** (LTS, Gradle toolchain으로 강제)
- **Spring Boot 3.5.14** (현 부트스트랩 시점 최신 3.5.x 패치)
- **Spring Security** + **Spring Data JPA** + `oauth2-resource-server`
- **Flyway 10.x** (`flyway-database-postgresql` 모듈 필수)
- **PostgreSQL 16+** (Supabase, vector/pgcrypto 확장 사용)
- **빌드: Gradle 8.14.4 wrapper, Groovy DSL** (`build.gradle`, `settings.gradle`) — Kotlin DSL 사용 안 함
- **언어: Java만** — Kotlin 사용 안 함

## 패키지 레이아웃 — DDD 절충형 (확정)

도메인별 패키지 + 그 안에서 4 레이어로 쪼갠다 (Spring + DDD 친화 절충안). JPA 엔티티 = aggregate root 를 `domain/` 에 두고, 헥사고날의 "domain model ≠ JPA entity" 분리까지는 가지 않는다 (매퍼 보일러플레이트와 `SELECT FOR UPDATE` 흐름의 복잡도 회피).

```
dev.seedo
├── SeedoApplication.java         ← @SpringBootApplication
├── config/                       ← SecurityConfig, JwtConfig, WebConfig
├── common/                       ← BaseEntity, BaseComment, 글로벌 에러 핸들러 (4 레이어 적용 안 함)
├── auth/                         ← Supabase JWT 검증 + RBAC
│   └── rbac/
│       ├── domain/               ← Role, Permission, UserRole, RolePermission
│       └── infrastructure/       ← *Repository
├── credit/
│   ├── domain/                   ← UserCredit (aggregate root), CreditTransaction, CreditType, CreditAmount(VO)
│   ├── application/              ← ChargeCreditService, ...
│   ├── infrastructure/           ← UserCreditRepository, CreditTransactionRepository
│   └── web/                      ← CreditController, DTO
├── user/                         ← 동일 4 레이어
├── idea/
├── project/
├── reward/
├── post/
├── ai/                           ← LLM 오케스트레이션 (선택적 헥사고날 — 외부 통합 격리)
├── search/                       ← 임베딩, RAG
└── admin/                        ← 관리자 액션
```

### 레이어 책임

| 레이어 | 책임 | 의존 |
|---|---|---|
| `domain/` | Aggregate root (= JPA `@Entity`), VO, Domain event, 비즈니스 규칙 | 같은/다른 도메인 `domain/`, common. JPA·Hibernate 어노테이션은 허용 |
| `application/` | 유스케이스 (`@Service @Transactional`). 트랜잭션 경계와 외부 호출 조합 | `domain/`, 자기 `infrastructure/` 또는 port out 인터페이스 |
| `infrastructure/` | 어댑터: `JpaRepository` 구현, 외부 API 클라이언트, 캐시 | Spring Data JPA, WebClient, Caffeine 등 framework |
| `web/` | REST Controller, request/response DTO, `@PreAuthorize` | `application/` |

> **방향**: `web → application → domain ← infrastructure`. `domain` 은 `application`/`web`/`infrastructure` 어떤 것도 import 하지 않는다.

### 선택적 헥사고날 — 외부 통합만

LLM·PG·이메일 등 **외부 시스템 통합**은 명시적으로 ports/adapter 패턴으로 격리:
- `<domain>/application/port/out/XxxClient.java` — 인터페이스 (도메인 어휘로)
- `<domain>/infrastructure/xxx/XxxWebClientAdapter.java` — 구현 (Resilience4j + WebClient)

DB 는 절충형으로 충분 — Hibernate dirty checking + `@Lock(PESSIMISTIC_WRITE)` 가 §8 트랜잭션 패턴과 잘 맞는다.

> 패키지 루트는 `dev.seedo`. Application 클래스명도 `SeedoApplication`.

## 주요 의존성과 의도

| 의존성 | 용도 | 메모 |
|---|---|---|
| `spring-boot-starter-web` | REST API | |
| `spring-boot-starter-security` | 인증·인가 | |
| `spring-boot-starter-data-jpa` | ORM | `ddl-auto: validate` 강제 |
| `spring-boot-starter-validation` | `@Valid` | |
| `spring-boot-starter-oauth2-resource-server` | JWT 검증 | nimbus-jose-jwt 자동 포함 → JWKS 검증 |
| `flyway-core` + `flyway-database-postgresql` | 마이그레이션 | Flyway 10+에서 PG 모듈 필수 |
| `postgresql` (runtime) | JDBC 드라이버 | |

추후 추가 예정:
- `spring-boot-starter-webflux` (OpenAI WebClient)
- `io.github.resilience4j:resilience4j-spring-boot3` (서킷·재시도)
- `com.github.ben-manes.caffeine:caffeine` (권한 캐시)
- `org.springframework.ai:spring-ai-openai-spring-boot-starter` (임베딩)

## 자주 쓰는 명령

```sh
./gradlew bootRun                  # 실행 (포트 8080)
./gradlew test                     # 테스트
./gradlew flywayInfo               # 마이그레이션 상태
./gradlew flywayMigrate            # 수동 적용 (보통 bootRun이 자동)
./gradlew build                    # 빌드 + 테스트
./gradlew clean build -x test      # 테스트 빼고 빌드
```

> Gradle wrapper(`./gradlew`)가 이미 들어 있다 — JDK 25만 깔려 있으면 바로 사용 가능.

## Flyway 마이그레이션 워크플로

1. 다음 V번호 확인
   ```sh
   ls src/main/resources/db/migration/
   ```
2. 새 파일 생성: `V<N>__<snake_case_summary>.sql`
   - 예: `V2__add_ideas_and_documents.sql`
   - 한 마이그레이션은 하나의 논리 단위(가능한 한 짧게).
3. **이미 적용된 마이그레이션은 절대 수정 금지** — 항상 새 V번호로 추가. 수정하면 checksum 불일치로 부팅 실패.
4. `bootRun` 또는 `flywayMigrate`로 적용. 적용 결과는 `flyway_schema_history` 테이블 확인.
5. 실패 시: 보통 SQL 오류 → 마이그레이션 파일 수정 후 재시도 (`schema_history`에 실패 row 남으면 수동 정리 필요할 수 있음).

### 마이그레이션 SQL 작성 규칙

- 시간 컬럼은 `timestamptz`
- enum 후보는 `varchar + CHECK`
- `updated_at` 갱신은 V1에 있는 `set_updated_at()` 트리거 재사용
- `credit_transactions`처럼 append-only 보장이 필요한 테이블은 트리거로 강제
- 인덱스 명명: `idx_<table>_<columns>` (`idx_users_nickname`, `idx_credit_tx_user_created`)
- partial UNIQUE는 `CREATE UNIQUE INDEX ... WHERE ...` 형태

### 시드 데이터 INSERT 시 주의

`serial` 컬럼에 id를 명시 INSERT 하면 시퀀스가 어긋남 → 마지막에 `setval` 필수:
```sql
SELECT setval('roles_id_seq', (SELECT MAX(id) FROM roles));
```

## Spring 구현 패턴

### 트랜잭션
```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public PurchaseResult purchase(...) { ... }
```
- 비관적 락은 JPA: `@Lock(LockModeType.PESSIMISTIC_WRITE)` + 리포지토리 메서드, 또는 native query `SELECT ... FOR UPDATE`.
- **외부 API(LLM/PG) 호출은 트랜잭션 안에 두지 않는다** — 호출 후 결과로 트랜잭션을 열거나, `@TransactionalEventListener(AFTER_COMMIT)`으로 분리.

### Security
- JWT 검증: `JwtDecoder` 빈을 JWKS URL로 구성 (`oauth2-resource-server`가 표준 처리).
- 사용자 권한 로딩: `UserDetailsService` 대신 JWT post-processor에서 DB 권한 조회 → `JwtAuthenticationConverter` 커스터마이징.
- 권한 캐시: Caffeine TTL 5분 (`@Cacheable("user-authorities")`).

### 외부 호출 (LLM/PG)
- `WebClient` + `WebClientCustomizer`로 타임아웃 30초.
- Resilience4j: 서킷 5회 연속 실패 → OPEN, 60초 후 HALF_OPEN. 일시 오류(429/503)만 3회 지수 백오프.

### JPA 엔티티
- `@Id` UUID는 `@JdbcTypeCode(SqlTypes.UUID)` (Hibernate 6 기본).
- 시간 컬럼은 `OffsetDateTime` 사용 (Postgres `timestamptz` 매핑).
- 댓글: `@MappedSuperclass BaseComment` → `IdeaComment`/`ProjectComment`/`PostComment` 상속 (테이블은 분리, 자바 코드만 공유).
- enum 후보 컬럼은 `@Enumerated(EnumType.STRING)` 또는 그냥 String 보유 + 비즈니스 레이어에서 enum 변환.

### DTO / 검증
- API 입출력은 record DTO 권장.
- `@Valid` + `@NotBlank` / `@Min` 등은 컨트롤러에서.
- 글로벌 예외 핸들러: `@RestControllerAdvice`에서 `MethodArgumentNotValidException`, 비즈니스 예외 → 표준 에러 응답.

## 설정 (application.yml)

- `spring.jpa.hibernate.ddl-auto: validate` — **절대 `update`/`create`로 바꾸지 말 것**.
- `spring.jpa.open-in-view: false` — N+1 마스킹 방지.
- `spring.flyway.baseline-on-migrate: true` — 빈 DB 첫 부팅용. 운영에서는 baseline-version도 명시.
- 시간대: `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`.

## 환경 변수 (로컬)

`.env` 또는 IDE run config로:
```
SUPABASE_DB_URL=jdbc:postgresql://db.<proj>.supabase.co:5432/postgres
SUPABASE_DB_USER=postgres
SUPABASE_DB_PASSWORD=...
SUPABASE_JWKS_URL=https://<proj>.supabase.co/auth/v1/.well-known/jwks.json
SUPABASE_JWT_ISSUER=https://<proj>.supabase.co/auth/v1
SUPABASE_SERVICE_ROLE_KEY=...
OPENAI_API_KEY=...
```

> Supabase 비번에 특수문자가 있으면 URL-encode 또는 `username/password`를 datasource로 분리. application.yml은 이미 분리해둠.

## 테스트

- 단위 테스트: 일반 JUnit 5 + Mockito. Spring 컨텍스트 띄우지 않음.
- 통합 테스트: `@SpringBootTest` + **Testcontainers Postgres** (운영과 동일 PG 버전). 의존성 (`spring-boot-testcontainers`, `testcontainers:postgresql`, `testcontainers:junit-jupiter`) 은 이미 추가되어 있다.
- 새 통합테스트는 `dev.seedo.support.AbstractIntegrationTest` 를 상속한다 — PG 16-alpine 컨테이너가 한 번만 부팅되고, Flyway 가 V1+ 를 적용한 datasource 가 자동 주입된다. 클래스명 컨벤션은 `*IT.java`.
- 절대 H2 등 인메모리 DB로 통합테스트 하지 말 것 — `timestamptz`, `vector`, partial index 등이 안 맞음.
- 트랜잭션 테스트는 동시성도 검증: 2 스레드로 동시 구매 → 한 건만 성공 확인.

## 자주 만나는 함정

- **Flyway checksum 불일치** — 적용된 V파일을 수정한 경우. 새 V번호로 추가하고 잘못 수정한 파일은 원복.
- **Supabase auth.users FK** — V1에는 의도적으로 미포함. Supabase 환경 전용 별도 마이그레이션에서 추가 (`auth` 스키마 의존). 로컬 PG로 테스트할 땐 그대로 두면 됨.
- **`open-in-view: true` 기본값** — 의도치 않은 lazy 로딩이 컨트롤러까지 새어나감 → 명시적으로 false.
- **Hibernate 6에서 sequence 기본 전략 변경** — `bigserial`과 호환되려면 `@GeneratedValue(strategy = GenerationType.IDENTITY)` 권장.
- **JWT issuer mismatch** — Supabase 프로젝트 URL 슬래시 끝까지 정확히 (`/auth/v1` vs `/auth/v1/`).
- **service_role 키 노출** — 절대 클라이언트 코드/로그에 찍지 말 것. 환경변수로만, 로깅 마스킹.

## 새 도메인 추가 시 체크리스트

1. Flyway V<N>__add_<domain>.sql 작성 (테이블 + 인덱스 + 트리거)
2. `dev.seedo.<domain>/{domain,application,infrastructure,web}` 4 레이어 분리. JPA 엔티티 = `domain/`, JpaRepository = `infrastructure/`, `@Service` = `application/`, `@RestController` = `web/`
3. 트랜잭션이 있으면 루트 `CLAUDE.md` §8의 트랜잭션 패턴 따름
4. 권한 필요하면 `permissions` 테이블에 코드 추가하는 마이그레이션 + `@PreAuthorize` 부착
5. 통합 테스트: 트랜잭션 정합성 + 동시성 한 케이스 이상
6. RLS도 같이 정의해야 하면 별도 마이그레이션으로 (Spring 우회 경로와 분리)
