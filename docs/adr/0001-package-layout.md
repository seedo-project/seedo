# ADR 0001 — 백엔드 패키지 레이아웃: 도메인별 4 레이어 (DDD 절충형)

- **Status**: Accepted
- **Date**: 2026-05-11
- **Deciders**: 백엔드 (yjinmo9), Claude Code 협업 세션
- **Tags**: `backend`, `package-structure`, `ddd`

---

## Context

Seedo 백엔드 (`backend/`, Spring Boot 3.5 + Java 25) 의 패키지 구조를 어떻게 잡을지 결정해야 했다. 토이/사이드 프로젝트 규모인데 트랜잭션 패턴 (크레딧 원장 + 멱등성 + `SELECT FOR UPDATE` + 비즈니스 인변식) 은 보통 회사 수준 — 구조 선택지가 양극단을 오갔다.

V1 (RBAC + 크레딧) 작업 직전, **`backend/CLAUDE.md` "패키지 레이아웃 — DDD 절충형 (확정)"** 섹션으로 이미 4 레이어를 박아두고 진행해왔다. PR #82 (#81 채택+보상) 리뷰 중 `reward/` 와 `user/` 가 4 레이어 중 2 개만 채워져 있는 게 눈에 띄어 이 결정을 다시 점검 — 그 결과 **현 구조 유지** 로 합의. 그 합의를 이 문서가 박제한다.

## Decision

**도메인별 패키지 (vertical slice) + 그 안에서 4 레이어** 로 쪼갠다.

```
dev.seedo
├── <domain>/                  // user, auth, credit, idea, project, reward, ...
│   ├── domain/                // Aggregate root (= JPA @Entity), VO, 상태 전이
│   ├── application/           // @Service @Transactional, 유스케이스
│   ├── infrastructure/        // JpaRepository, 외부 API 어댑터, 캐시
│   └── web/                   // RestController, request/response DTO, ExceptionHandler
├── common/                    // BaseEntity, web 공유 인프라 (ApiResponse, @CurrentUserId)
├── config/                    // SecurityConfig, WebMvcConfig, CacheConfig
└── SeedoApplication.java
```

**방향**: `web → application → domain ← infrastructure`. `domain` 은 다른 레이어를 import 하지 않는다.

**4 레이어는 다 채워야 하는 게 아니라 필요할 때 채우는 가이드라인.** 외부 진입점이 없는 도메인은 `application`/`web` 을 비워둘 수 있다 — 예: `reward/`, `user/` 는 현재 `domain` + `infrastructure` 만.

**선택적 헥사고날 — 외부 통합만 적용**:
- LLM, PG, 이메일 등 외부 시스템 통합은 `application/port/out/XxxClient` 인터페이스 + `infrastructure/xxx/XxxAdapter` 구현으로 격리.
- DB 는 절충형으로 충분 — Hibernate dirty checking + `@Lock(PESSIMISTIC_WRITE)` 가 트랜잭션 패턴과 잘 맞는다.

JPA 엔티티 = aggregate root 를 `domain/` 에 두고, **헥사고날의 "domain model ≠ JPA entity" 분리까지는 가지 않는다.** 매퍼 보일러플레이트 + `SELECT FOR UPDATE` 흐름의 복잡도 회피.

## Alternatives Considered

### Alt A — 레이어별 평면 (가장 흔한 Spring 패턴)

```
dev.seedo/
├── controller/
├── service/
├── repository/
├── entity/
└── dto/
```

- **장점**: 누구나 알아본다. 부트캠프 / 신입 / 토이프로젝트의 표준
- **단점**: 도메인 늘면 한 패키지 안에 클래스 수십 개. 한 비즈니스 흐름 (예: 아이디어 구매) 코드가 5 개 패키지에 흩어진다. 도메인 경계가 모호해진다
- **반려 이유**: Seedo 의 도메인 (user/credit/idea/project/reward) 이 명확히 분리되는 컨텍스트라 vertical slice 가 명백한 우위

### Alt B — 도메인별 평면 (Hybrid)

```
dev.seedo/
├── user/
│   ├── User.java
│   ├── UserController.java
│   ├── UserService.java
│   └── UserRepository.java
└── ...
```

- **장점**: vertical slice + 보일러플레이트 최소. 우아한테크코스 등에서 권장
- **단점**: 도메인 안 클래스 수가 늘면 다시 평면 패키지의 문제 재발. 트랜잭션이 복잡한 도메인 (credit, idea, project) 은 `Service`/`Controller`/`Repository` 가 클래스 한두 개로 끝나지 않음 — 4~10 개씩 됨
- **반려 이유**: 채택 트랜잭션 (`AdoptIdeaService` + Command/Result/Exception 4 개) 만 봐도 `application/` 하위에 5+ 클래스. 평면이면 `project/` 가 10+ 클래스로 비대해진다

### Alt C — 엄격 헥사고날 / Onion (가장 형식적)

```
dev.seedo/
├── adapter/
│   ├── in/web/
│   └── out/persistence/
├── application/
│   ├── port/in/
│   ├── port/out/
│   └── service/
└── domain/
```

- **장점**: 도메인 = JPA 엔티티 분리, 포트/어댑터 풀 적용. 테스트 격리 최고
- **단점**: 매퍼 보일러플레이트, `SELECT FOR UPDATE` / Hibernate dirty checking 같은 ORM 친화 패턴이 헐거워짐. 토이 프로젝트엔 명백한 오버엔지니어링
- **반려 이유**: Seedo 의 트랜잭션 패턴은 JPA 친화. 도메인 = 엔티티 절충이 더 가치 있음

### Alt D — 우리 선택 (DDD 절충형, "Choose")

**Alt B 의 vertical slice + Alt C 의 4 레이어 분할 + JPA-도메인 동일시**. 외부 통합만 ports/adapter 로 격리.

## Consequences

### 좋은 점

- **트랜잭션 경계 명확**: `application/` 의 `@Service @Transactional` 메서드 단위로 비즈니스 트랜잭션이 모인다 — 잔액·원장 갱신 / 채택 7 단계 같은 패턴이 한 메서드에 응집
- **도메인 어휘 보존**: `Project.create() / archive() / softDelete()` 같은 상태 전이가 도메인 메서드로 강제. DB CHECK 는 2 차 방어선
- **격리된 ExceptionHandler**: `@ControllerAdvice(basePackages = "dev.seedo.<domain>.web")` 로 도메인별 4xx 매핑 충돌 없이 분리 (`IdeaExceptionHandler` vs `ProjectExceptionHandler`)
- **외부 통합 격리 자리 확보**: OpenAI 임베딩 같은 외부 호출이 들어올 때 `application/port/out` + `infrastructure/adapter` 패턴 자리가 미리 잡혀 있음
- **포트폴리오 / 시니어 면접 관점 플러스**

### 나쁜 점

- **학습 곡선**: 한국 Spring 신입~중급 환경의 표준 (Alt A) 과 다르다. 새 팀원 합류 시 5~10 분 설명 필요
- **단순 CRUD 에는 보일러플레이트**: 사용자 프로필 같은 단순 도메인도 4 레이어 풀로 가면 과함
  - **완화책**: `user/` 와 `reward/` 처럼 외부 진입점 없는 도메인은 `application/`, `web/` 을 비워둔다 (가이드라인을 풀 강제로 안 읽는다)
- **레이어 간 import 규칙 어기기 쉬움**: `domain` 이 `infrastructure` 를 import 하는 실수 — 코드 리뷰에서 잡아야 함
- **약간의 클래스 수 증가**: Command/Result record DTO 같은 게 늘어남

### 일관성 규칙

- 4 레이어 풀 적용: `credit`, `idea`, `project`
- 2 레이어 (`domain` + `infrastructure`) 만: `user`, `reward` — 외부 REST 진입점이 없거나 다른 도메인의 트랜잭션 안에서만 호출되는 경우
- `auth/rbac/` 처럼 sub-domain 패턴도 허용

## When to Revisit

다음 중 하나가 발생하면 이 결정을 다시 점검:

- 도메인 수가 15 개를 넘어가고 `application/` 안의 service 가 평균 5+ 개로 부풀어 헥사고날 풀 (`port/in/out`) 의 가치가 명백해질 때
- 도메인 모델과 JPA 엔티티의 결합이 진짜 문제로 드러날 때 (예: 도메인 이벤트 시스템이 본격 도입되어 엔티티 변경이 외부 컨슈머에게 영향이 클 때)
- 팀이 커져서 (3 명 → 6 명+) 신규 합류자의 학습 곡선이 작업 속도를 눈에 띄게 떨어뜨릴 때 — 그 시점엔 Alt B 로 일부 도메인 평면화 검토

## Links

- `backend/CLAUDE.md` "패키지 레이아웃 — DDD 절충형 (확정)" — 일상 작업용 짧은 가이드
- `CLAUDE.md` §10.3 "Spring" — 트랜잭션·캐시·외부 호출 컨벤션
- Vaughn Vernon, *Implementing Domain-Driven Design* — 이 ADR 의 원본 사상 (절충 부분 제외)
- Alistair Cockburn, *Hexagonal Architecture* — Alt C 의 출발점
