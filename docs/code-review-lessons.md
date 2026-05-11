# 코드 리뷰에서 배운 것들

CodeRabbit / 동료 리뷰에서 반복적으로 나오는 패턴과 그 결과를 모아둔다. 단순 PR 변경 이력이 아니라 **다시 같은 실수를 안 하기 위한 학습 노트** — 새 PR 작성 전, 비슷한 트랜잭션/도메인/스키마 작업 직전에 훑어보면 사고를 줄일 수 있다.

ADR (`docs/adr/`) 과의 차이:
- ADR — "구조/스택 결정" 의 배경 (큰 결정, 드물게 추가)
- 이 문서 — "코드 디테일 패턴" 학습 (잔잔한 교훈, 자주 추가)

---

## 새 리뷰 항목 추가 컨벤션

내가 (사용자) CodeRabbit 리뷰 본문을 붙여넣으면 → Claude 가 이 문서의 적절한 카테고리에 새 항목으로 추가한다. 형식은 기존 항목과 동일:

```
### [N.N] 한 줄 요약 — 결정 (✅ 반영 / ❌ 무시 / ⏳ 추후 / 🔄 부분 반영)

**출처**: PR #NN

**지적**: CodeRabbit 이 짚은 내용 한두 문장

**전 / 후 코드** (반영한 경우):
```언어
// 전
...
```
```언어
// 후
...
```

**판단 근거**: 왜 반영 / 무시 / 추후로 분류했는지

**교훈**: 다음에 비슷한 상황에서 어떻게 처음부터 짤지
```

새 항목은 카테고리 안의 마지막에 추가. 번호는 카테고리 안에서 1 부터 증가. 카테고리 자체가 새로 필요하면 "## 카테고리 N — 이름" 으로 추가하고 목차도 갱신.

---

## 목차

- [A. 트랜잭션 / 동시성](#a-트랜잭션--동시성)
- [B. 도메인 가드 (코드 레이어)](#b-도메인-가드-코드-레이어)
- [C. DB 가드 (마이그레이션)](#c-db-가드-마이그레이션)
- [D. 에러 핸들링 / HTTP 매핑](#d-에러-핸들링--http-매핑)
- [E. JPA / 영속성](#e-jpa--영속성)
- [F. 테스트 안정성](#f-테스트-안정성)
- [H. 외부 호출 / 통합](#h-외부-호출--통합)
- [G. 의도적으로 안 받은 지적](#g-의도적으로-안-받은-지적)
- [PR 인덱스](#pr-인덱스)

---

## A. 트랜잭션 / 동시성

### A.1 잔액 변경은 `SELECT FOR UPDATE` 안에서 — ✅

**출처**: PR #71 (`UserCredit`), PR #74 (구매), PR #80 (finalize), PR #82 (채택)

**지적**: 잔액 조회 → 검증 → 갱신 사이에 다른 트랜잭션이 끼면 잔액이 깨진다. `findById` 만 쓰면 락이 없다.

**전**:
```java
UserCredit credit = creditRepo.findById(userId).orElseThrow(...);
credit.applyDelta(amount);
```

**후**:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select c from UserCredit c where c.userId = :id")
Optional<UserCredit> findByIdForUpdate(@Param("id") UUID id);
```
```java
UserCredit credit = creditRepo.findByIdForUpdate(userId).orElseThrow(...);
credit.applyDelta(amount);
```

**교훈**: 잔액 / 상태 전이 / 카운터처럼 "읽고 → 검증하고 → 갱신" 패턴이면 **무조건** `findByIdForUpdate` 같은 `@Lock(PESSIMISTIC_WRITE)` 메서드를 따로 둔다. `findById` 는 단순 조회 전용.

---

### A.2 락 메서드는 `@Param` 명시 — ✅

**출처**: PR #71

**지적**: 컴파일 옵션에 `-parameters` 가 없으면 `@Param` 없는 메서드의 바인딩이 실패할 수 있다.

**전**:
```java
@Query("select c from UserCredit c where c.userId = :id")
Optional<UserCredit> findByIdForUpdate(UUID id);
```

**후**:
```java
@Query("select c from UserCredit c where c.userId = :id")
Optional<UserCredit> findByIdForUpdate(@Param("id") UUID id);
```

**교훈**: JPQL `:name` 바인딩에는 항상 `@Param("name")` 박는다. 빌드 설정 의존을 안 만든다.

---

### A.3 같은 세션/리소스에 대한 동시 호출 직렬화 — ✅

**출처**: PR #80 (CodeRabbit #80 의 핵심 지적)

**지적**: `FinalizeChatSessionService.findById` 가 락 없어서 같은 세션에 동시 finalize 두 건이 모두 IN_PROGRESS 가드를 통과 → 두 (idea, doc) 쌍 INSERT → 세션 UPDATE 만 last-writer-wins → 패자 (idea, doc) 가 고아로 남음.

**전**:
```java
IdeaChatSession session = sessionRepo.findById(cmd.sessionId())
    .orElseThrow(...);
if (session.getStatus() != IN_PROGRESS) throw ...;
// (이어서 idea/doc INSERT)
```

**후**:
```java
IdeaChatSession session = sessionRepo.findByIdForUpdate(cmd.sessionId())
    .orElseThrow(...);
if (session.getStatus() != IN_PROGRESS) throw ...;
```

**교훈**: "상태 전이 한 번만" 류 리소스는 **그 리소스 자체에** 락을 잡는다. DB CHECK 는 *단일 row* 정합성만 보호 — race-time 으로 두 row 가 생기는 시나리오는 락이 막아야 한다. PR 머지 전 self-check: "이 트랜잭션이 동시에 두 번 들어오면 데이터가 어떻게 깨지나?" 한 번 더 생각.

---

### A.4 동시성 IT 는 `ready latch` 로 진짜 경쟁 강제 — ✅

**출처**: PR #80, PR #82 (CodeRabbit 의 정확한 지적)

**지적**: `CountDownLatch start = new CountDownLatch(1)` 하나만 쓰면 한 워커가 늦게 `await()` 도착했을 때 사실상 순차 실행이 된다. 그러면 락 회귀 (PESSIMISTIC_WRITE 제거) 가 일어나도 테스트가 통과해버린다.

**전**:
```java
CountDownLatch start = new CountDownLatch(1);
Future<?> a = exec.submit(() -> { start.await(); return service.x(); });
Future<?> b = exec.submit(() -> { start.await(); return service.x(); });
start.countDown(); // 두 워커가 모두 await 에 도달했는지 보장 없음
```

**후**:
```java
CountDownLatch ready = new CountDownLatch(2);
CountDownLatch start = new CountDownLatch(1);
Future<?> a = exec.submit(() -> {
    ready.countDown();
    start.await();
    return service.x();
});
Future<?> b = exec.submit(() -> {
    ready.countDown();
    start.await();
    return service.x();
});
assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
start.countDown();
```

**교훈**: 동시성 IT 는 "동시에 출발했다" 를 직접 검증해야 한다 — `ready` 카운트다운 2 → `ready.await()` → `start.countDown()` 의 3-step 패턴이 표준. 이게 없으면 락 가드를 빼도 테스트가 통과해서 회귀 검출력 0.

---

### A.5 다음 버전 계산은 `idea row 락` 안에서 — 🔄 (부분 반영)

**출처**: PR #80

**지적**: `documentRepo.findFirstByIdeaIdOrderByVersionDesc(...)` 가 락 없이 MAX+1 을 읽으면 두 동시 publish 가 같은 v2 를 시도할 수 있다.

**우리 선택**: `PublishIdeaVersionService` 가 먼저 `ideaRepo.findByIdForUpdate(ideaId)` 로 idea row 를 락 → 그 안에서 MAX+1 조회. 즉 idea row 가 직렬화 게이트가 된다. document 레벨에 별도 락은 안 만듦. UNIQUE(idea_id, version) 가 마지막 방어선.

**교훈**: 자식 row 충돌은 **부모 row 락** 으로 직렬화하는 게 깔끔하다. document 마다 락 거는 것보다 idea 락 안에서 다 끝내는 게 응집도 ↑.

---

## B. 도메인 가드 (코드 레이어)

### B.1 생성자에서 음수/범위 검증 — ✅

**출처**: PR #61 (Idea), PR #71 (CreditAmount)

**지적**: `new Idea(author, -10, -5)` 같은 호출을 도메인이 받아들이면 DB CHECK 까지 가서야 막힘. 사용자에게 도달하는 에러도 5xx.

**전**:
```java
public Idea(UUID authorId, int priceCredits, int rewardCredits) {
    this.authorId = authorId;
    this.priceCredits = priceCredits;
    this.rewardCredits = rewardCredits;
    this.status = IdeaStatus.DRAFT;
}
```

**후**:
```java
public Idea(UUID authorId, int priceCredits, int rewardCredits) {
    if (priceCredits < 0 || rewardCredits < 0) {
        throw new IllegalArgumentException(
            "credits must be non-negative: price=" + priceCredits + ", reward=" + rewardCredits);
    }
    this.authorId = authorId;
    this.priceCredits = priceCredits;
    this.rewardCredits = rewardCredits;
    this.status = IdeaStatus.DRAFT;
}
```

**교훈**: 도메인 생성자 / 팩토리는 **DB CHECK 와 같은 식** 의 검증을 거기서도 한다. 1 차 방어선이 도메인, 2 차가 DB. 1 차가 빠지면 5xx 만 돌아오고 무엇이 잘못됐는지 안 보인다.

---

### B.2 상태 전이는 도메인 메서드로만 — ✅

**출처**: PR #61 (Idea), PR #79 (IdeaChatSession), PR #82 (Project)

**지적**: setter 또는 직접 필드 변경으로 `DELETED → PUBLISHED` 같은 비정상 전이가 가능하면 DB 도착 전까지 잡힐 길이 없다.

**전**:
```java
public void setStatus(IdeaStatus s) { this.status = s; }
```

**후**:
```java
// setter 없음. 전이는 명명된 메서드만.
public void publish() {
    if (status != IdeaStatus.DRAFT) {
        throw new IllegalStateException("publish requires DRAFT, was " + status);
    }
    this.status = IdeaStatus.PUBLISHED;
}
public void archive() { ... }
public void softDelete() { ... }
```

**교훈**: aggregate root 의 상태 컬럼은 setter 노출 금지. 각 전이가 명명된 메서드, 메서드 안에서 현재 상태 검증. 동일 패턴을 `ChatSessionStatus.finalize/abandon`, `ProjectStatus.recruit/start/complete/archive/softDelete` 에 모두 적용.

---

### B.3 같은 시그니처에 특수 역할 가드 — ✅

**출처**: PR #82 (ProjectMember.leave)

**지적**: `leave()` 가 role 무관하게 `leftAt` 만 세팅하면 LEADER 도 탈퇴 가능 → 활성 리더 0 명, `projects.leader_id` 와 어긋남.

**전**:
```java
public void leave() {
    if (leftAt != null) throw new IllegalStateException("already left");
    this.leftAt = OffsetDateTime.now(...);
}
```

**후**:
```java
public void leave() {
    if (leftAt != null) throw new IllegalStateException("already left");
    if (role == ProjectMemberRole.LEADER) {
        throw new IllegalStateException(
            "LEADER cannot leave directly — use transferLeadership to demote first");
    }
    this.leftAt = OffsetDateTime.now(...);
}
```

**교훈**: 도메인 메서드 시그니처가 일반적 (member.leave) 이라도 안에서 **역할별 분기 / 거부** 를 해야 할 때가 있다. 일반 동작 + 특수 역할 가드 / 별도 이관 메서드 (transferLeadership) 의 2-track 으로 설계.

---

### B.4 `record` 다층 방어선 — ✅

**출처**: PR #71 (CreditAmount)

**지적**: `record CreditAmount(long value)` 는 기본 생성자가 public 이라 `new CreditAmount(-100)` 우회가 항상 가능.

**우리 선택**: record 유지 + compact constructor 에 가드 추가. 자가 우회는 `IllegalArgumentException` 으로 던지고, 마지막 방어선은 DB CHECK.

```java
public record CreditAmount(long value) {
    public CreditAmount {
        // 1차 방어 — 도메인 생성 시점
        // (sign 검증은 type 과의 조합에 달려 있으므로 type 검증 메서드에서 처리)
    }
}
```

**교훈**: record 의 캡슐화는 class private constructor 만큼 강하지 않다. 1 인 MVP 에선 record + compact constructor + DB CHECK 의 다층 방어선이 충분. 향후 SDK 외부 노출 같은 시나리오면 class + factory 로 승격.

---

## C. DB 가드 (마이그레이션)

### C.1 partial UNIQUE 로 "유일한 활성 row" 강제 — ✅

**출처**: PR #82 V5 (활성 LEADER 1 명), PR #82 V4 (idea_purchases active membership), PR #82 (rewards.idea_id ADOPTION)

**지적**: 단순 UNIQUE 는 모든 row 에 적용 — 탈퇴/취소된 row 의 재가입을 막아버린다. "현재 활성 인 것 중 유일" 을 원하면 `WHERE` 절 partial UNIQUE.

**예 1 — 활성 LEADER 1 명**:
```sql
CREATE UNIQUE INDEX project_members_active_leader_uniq
    ON project_members(project_id)
    WHERE role = 'LEADER' AND left_at IS NULL;
```

**예 2 — 한 아이디어 → 첫 채택자만 보상**:
```sql
CREATE UNIQUE INDEX rewards_adoption_idea_uniq
    ON rewards(idea_id)
    WHERE reward_type = 'ADOPTION';
```

**예 3 — 한 사용자가 한 아이디어 한 번만**:
```sql
CREATE UNIQUE INDEX -- (V2 idea_purchases)
    ON idea_purchases(idea_id, buyer_id);  -- 이건 풀 UNIQUE 라도 의도 일치
```

**교훈**: 정책 ("한 프로젝트에 리더 1 명", "첫 채택자만 보상") 을 코드 가드뿐 아니라 **DB partial UNIQUE** 로 강제. service 사전 체크는 race window 가 있을 수 있다 — 마지막 방어선은 DB.

---

### C.2 양방향 CHECK 로 컬럼 정합성 — ✅

**출처**: PR #45 (V2 ideas), PR #82 (V4 projects/rewards)

**지적**: 상태와 부수 컬럼이 짝을 이루는데 한쪽만 검증하면 비정합 row 가 들어갈 수 있다.

**전**:
```sql
status varchar(20) CHECK (status IN ('ACTIVE','DELETED'))
deleted_at timestamptz
-- DELETED 인데 deleted_at NULL, ACTIVE 인데 deleted_at NOT NULL 모두 허용됨
```

**후**:
```sql
CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
```

다른 예 (PR #82 rewards):
```sql
CHECK ((reward_type = 'ADOPTION') = (idea_id IS NOT NULL))
CHECK ((status = 'PAID') = (transaction_id IS NOT NULL))
CHECK ((status = 'PAID') = (paid_at IS NOT NULL))
```

**교훈**: 상태값 ↔ 부수 컬럼 짝은 `(A) = (B)` 양방향 CHECK. 한 줄로 양쪽 다 막힌다 — "상태는 X 인데 부수가 NULL", "상태는 Y 인데 부수가 채워짐" 모두 비정합.

---

### C.3 append-only 테이블은 트리거로 UPDATE/DELETE 차단 — ✅

**출처**: PR #71 V1 (`credit_transactions`)

**지적**: 원장 테이블은 정정 시 새 ADJUST 행을 추가해야 정합성 유지. 직접 UPDATE 허용하면 잔액 캐시와 어긋날 수 있다.

**예**:
```sql
CREATE OR REPLACE FUNCTION block_credit_transaction_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION
        'credit_transactions is append-only — UPDATE/DELETE 금지. 정정은 type=ADJUST 새 row 로 처리.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER block_credit_tx_update
BEFORE UPDATE ON credit_transactions
FOR EACH ROW EXECUTE FUNCTION block_credit_transaction_mutation();

CREATE TRIGGER block_credit_tx_delete
BEFORE DELETE ON credit_transactions
FOR EACH ROW EXECUTE FUNCTION block_credit_transaction_mutation();
```

**교훈**: 원장 / 감사로그 / 보상 정산처럼 "한 번 박히면 변경 금지" 류 테이블은 트리거로 못 박는다. 코드만 신뢰하면 실수 한 번에 회복 불가.

---

### C.4 멱등성 키 partial UNIQUE — ✅

**출처**: PR #71 V1 (`credit_transactions`)

**지적**: PG webhook / 외부 시스템 재시도가 멱등하려면 키 컬럼을 UNIQUE 로 보호.

**예**:
```sql
reference_type varchar(50),
reference_id   varchar(100),
CHECK ((reference_type IS NULL) = (reference_id IS NULL))

CREATE UNIQUE INDEX credit_tx_reference_uniq
    ON credit_transactions(reference_type, reference_id)
    WHERE reference_type IS NOT NULL AND reference_id IS NOT NULL;
```

**교훈**: 외부 호출 (webhook, 외부 API 콜백) 의 멱등성은 **사전 SELECT + DB UNIQUE** 두 줄 방어. 서비스가 사전 체크 race 로 빠져나가도 DB 가 잡는다.

---

## D. 에러 핸들링 / HTTP 매핑

### D.1 `@ExceptionHandler` 에 `@ResponseStatus` 조합 금지 — ✅

**출처**: PR #75 (CodeRabbit 후속)

**지적**: `@ExceptionHandler` 메서드에 `@ResponseStatus` 를 같이 붙이면 응답 본문이 누락되는 케이스가 발생.

**전**:
```java
@ExceptionHandler(AlreadyPurchasedException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public ApiResponse<Void> handle(AlreadyPurchasedException e) {
    return ApiResponse.error(e.getMessage());
}
// → 일부 경로에서 body 가 빠진다
```

**후**:
```java
@ExceptionHandler(AlreadyPurchasedException.class)
public ResponseEntity<ApiResponse<Void>> handle(AlreadyPurchasedException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(e.getMessage()));
}
```

**교훈**: 예외 핸들러는 항상 `ResponseEntity.status(...).body(...)` 패턴으로 — 상태 코드와 본문을 같이 보장. `@ResponseStatus` 단독 사용은 컨트롤러 반환 타입에서만.

---

### D.2 `DataIntegrityViolationException` 은 SQLState 로 분류해서 잡기 — ✅

**출처**: PR #74 (CodeRabbit 후속), PR #82 (`ProjectExceptionHandler`)

**지적**: catch-all `@ExceptionHandler(DataIntegrityViolationException.class)` 로 다 409 매핑하면 FK 위반, NOT NULL 위반, 다른 테이블의 UNIQUE 위반까지 모두 같은 응답 — 버그를 4xx 로 숨긴다.

**전**:
```java
@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ApiResponse<Void>> handle(DataIntegrityViolationException e) {
    return error(HttpStatus.CONFLICT, "duplicate");
}
```

**후**:
```java
@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ApiResponse<Void>> handle(DataIntegrityViolationException e) {
    if (isIdeaPurchaseUniqueViolation(e)) {
        return error(HttpStatus.CONFLICT, "already purchased");
    }
    throw e;  // 다른 무결성 위반은 5xx 로 노출
}

private boolean isIdeaPurchaseUniqueViolation(DataIntegrityViolationException e) {
    Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
    if (!(root instanceof SQLException sqlEx)) return false;
    if (!"23505".equals(sqlEx.getSQLState())) return false;
    return sqlEx.getMessage() != null && sqlEx.getMessage().contains("idea_purchases");
}
```

**교훈**: DB 예외는 **SQLState + 인덱스/제약 이름** 두 가지로 좁혀서 잡는다. 그 외는 다시 던져 Spring 기본 5xx 로 — race-time UNIQUE 충돌만 4xx, 진짜 버그는 노출.

| SQLState | 의미 |
|---|---|
| 23505 | unique 위반 |
| 23514 | check 위반 |
| 23503 | FK 위반 |
| P0001 | PL/pgSQL RAISE EXCEPTION (트리거) |

---

### D.3 도메인별 advice 패키지 격리 — ✅

**출처**: PR #82 (`ProjectExceptionHandler`)

**지적**: `@ControllerAdvice` 가 전역이면 도메인 간 예외 매핑이 충돌하거나 다른 도메인의 무관한 예외에도 끼어듦.

**후**:
```java
@ControllerAdvice(basePackages = "dev.seedo.idea.web")
public class IdeaExceptionHandler { ... }

@ControllerAdvice(basePackages = "dev.seedo.project.web")
public class ProjectExceptionHandler { ... }
```

**교훈**: advice 는 `basePackages` 로 도메인 web 패키지에 한정. 다른 도메인 컨트롤러는 자기 advice 가 처리. 공유 예외 (`IdeaNotFoundException` 같은) 는 양쪽 advice 에서 동일 매핑 — 도메인 격리 유지하면서 재사용.

---

### D.4 application 예외에 Spring Web 의존 박지 않기 — ✅

**출처**: PR #74

**지적**: `AlreadyPurchasedException` 에 `@ResponseStatus(409)` 같은 어노테이션 박으면 application 레이어가 Spring Web 에 의존 (4 레이어 방향 위반).

**전**:
```java
@ResponseStatus(HttpStatus.CONFLICT)  // application 가 Spring Web import
public class AlreadyPurchasedException extends RuntimeException { ... }
```

**후**:
```java
// application/AlreadyPurchasedException — Spring 의존 없는 순수 RuntimeException
public class AlreadyPurchasedException extends RuntimeException {
    public AlreadyPurchasedException(Long ideaId, UUID buyerId) {
        super("already purchased: ideaId=" + ideaId + ", buyerId=" + buyerId);
    }
}

// web/IdeaExceptionHandler — HTTP 매핑은 web 레이어에서
@ExceptionHandler(AlreadyPurchasedException.class)
public ResponseEntity<ApiResponse<Void>> handle(AlreadyPurchasedException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(e.getMessage()));
}
```

**교훈**: 예외 타입은 application/도메인에 두되 HTTP 매핑은 web/advice 에. 4 레이어 방향 (`web → application → domain ← infrastructure`) 유지.

---

### D.5 `IllegalArgumentException` 을 typed 비즈니스 예외로 승격 — ✅

**출처**: PR #74

**지적**: `UserCredit.applyDelta` 가 `IllegalArgumentException` 던지면 Spring 기본 500 으로 노출 → 잔액 부족이 5xx.

**전**:
```java
credit.applyDelta(amount);  // 잔액 < 0 면 IllegalArgumentException
// → 호출자 잡지 않으면 500
```

**후**:
```java
try {
    credit.applyDelta(amount);
} catch (IllegalArgumentException e) {
    throw new InsufficientCreditException(cmd.userId(), balanceBefore, cmd.amount());
}
```

```java
// IdeaExceptionHandler
@ExceptionHandler(InsufficientCreditException.class)
public ResponseEntity<ApiResponse<Void>> handle(InsufficientCreditException e) {
    return error(HttpStatus.BAD_REQUEST, e.getMessage());
}
```

**교훈**: 도메인의 일반 예외 (`IllegalArgumentException`, `IllegalStateException`) 를 비즈니스 시나리오 (`InsufficientCreditException`, `IdeaNotAdoptableException`) 로 변환해 typed 매핑. 5xx vs 4xx 가 정확히 갈린다.

---

## E. JPA / 영속성

### E.1 `Persistable<UUID>` 구현 — ✅ (`User`)

**출처**: PR #45 (V1 users)

**지적**: `users.id` 가 외부에서 박는 UUID (Supabase auth.users.id) 인데 `save()` 호출 시 JPA 가 "이미 존재하나?" SELECT 부터 돌림 → 매 INSERT 마다 불필요한 SELECT.

**전**:
```java
@Entity
public class User extends BaseEntity {
    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;
    // ...
}
// save(new User(...)) → SELECT + INSERT (네트워크 RT 2회)
```

**후**:
```java
@Entity
public class User extends BaseEntity implements Persistable<UUID> {
    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Transient
    @Override
    public boolean isNew() {
        return getCreatedAt() == null;  // BaseEntity 의 @CreationTimestamp 가 채우기 전이면 new
    }
}
// save(new User(...)) → INSERT 만 (1 회)
```

**교훈**: PK 가 외부 주입 (UUID, 자연 키) 이면 `Persistable` 을 구현해 "이건 새 row" 를 JPA 에게 알려준다. `isNew()` 판별 기준은 `createdAt == null` 같은 영속화 후 채워지는 컬럼.

---

### E.2 잔액 같은 컬럼은 setter 노출 안 함 — ✅

**출처**: PR #71 (UserCredit)

**지적**: `setBalance(long)` 노출하면 어디서든 잔액 변경 가능 — 원장과 어긋남.

**후**:
```java
@Entity
public class UserCredit {
    @Id
    private UUID userId;

    @Column(nullable = false)
    private long balance;  // setter 없음

    /** balance 변경은 applyDelta 만 — 음수면 IllegalArgumentException. */
    public void applyDelta(long delta) {
        long next = this.balance + delta;
        if (next < 0) {
            throw new IllegalArgumentException(
                "balance must be non-negative: current=" + balance + ", delta=" + delta);
        }
        this.balance = next;
    }
}
```

**교훈**: 비즈니스 의미 있는 필드는 명명된 의도 메서드 (`applyDelta`, `charge`, `refund`) 로만 변경. JPA dirty checking 이 commit 시 UPDATE.

---

### E.3 IT 에서 `em.clear()` 로 1 차 캐시 분리 — ✅

**출처**: PR #45 (Idea persistence IT)

**지적**: `save()` 후 같은 영속성 컨텍스트에서 `findById` 하면 1 차 캐시가 INSERT 안 한 메모리 상태를 그대로 반환 — DB 가 진짜로 저장했는지는 검증 안 됨.

**전**:
```java
Idea saved = ideaRepo.saveAndFlush(new Idea(...));
Idea loaded = ideaRepo.findById(saved.getId()).orElseThrow();
// loaded == saved (1차 캐시 hit)
```

**후**:
```java
Idea saved = ideaRepo.saveAndFlush(new Idea(...));
em.clear();
Idea loaded = ideaRepo.findById(saved.getId()).orElseThrow();
// 진짜 DB 에서 다시 읽음
```

**교훈**: IT 에서 "정말로 DB 에 박혔는지" 검증해야 할 때 `em.clear()`. 단순 동작 검증엔 불필요 — 컬럼/제약 invariant IT 에서 특히 중요.

---

### E.4 application → infrastructure 직접 의존 허용 — ❌ (의도적)

**출처**: PR #74

**지적**: `PurchaseIdeaService` (application) 가 `IdeaRepository` (infrastructure) 를 직접 import — 헥사고날 strict 에선 port out 인터페이스로 격리해야.

**우리 답변**: CLAUDE.md 의 4 레이어 절충형 ([ADR 0001](adr/0001-package-layout.md)) — DB 는 절충, 외부 통합 (OpenAI/PG/이메일) 만 ports/adapter 격리. JPA repository 직접 의존은 의도된 선택.

**교훈**: 모든 의존을 인터페이스로 격리하면 매퍼 보일러플레이트 폭증. JPA 친화 절충은 의식적 선택이고 ADR 에 박혔다. 비슷한 지적이 또 오면 ADR 링크로 답글.

---

## F. 테스트 안정성

### F.1 메시지 문구 의존 → SQLSTATE 검증 — ✅

**출처**: PR #45 (V1 invariant IT)

**지적**: `assertThatThrownBy(...).hasMessageContaining("violates check constraint")` 는 PostgreSQL locale / 드라이버 버전에 따라 깨진다.

**전**:
```java
assertThatThrownBy(() -> save(...))
    .hasMessageContaining("violates check constraint");
```

**후**:
```java
// AbstractIntegrationTest 의 헬퍼
public static void assertSqlState(Throwable thrown, String expectedSqlState) {
    Throwable cursor = thrown;
    while (cursor != null) {
        if (cursor instanceof SQLException sqlEx) {
            assertThat(sqlEx.getSQLState()).isEqualTo(expectedSqlState);
            return;
        }
        cursor = cursor.getCause();
    }
    throw new AssertionError("No SQLException in cause chain");
}

// 테스트
assertThatThrownBy(() -> save(...))
    .satisfies(t -> assertSqlState(t, SQLSTATE_CHECK_VIOLATION));
// SQLSTATE_CHECK_VIOLATION = "23514"
```

**교훈**: DB 제약 위반은 SQLState 코드로 검증. 메시지는 환경 따라 바뀐다.

---

### F.2 race 테스트에서 예외 타입 명시 검증 — ✅

**출처**: PR #74 (CodeRabbit 후속)

**지적**: 동시성 테스트에서 패자가 어떤 예외든 받기만 하면 OK 로 통과시키면, 다른 버그 (NPE 등) 가 끼어들어도 안 잡힌다.

**전**:
```java
// 둘 중 하나는 실패해야 한다 — 어떤 실패든 통과
assertThat(errors).hasSize(1);
```

**후**:
```java
assertThat(errors).hasSize(1);
assertThat(errors.get(0))
    .isInstanceOfAny(AlreadyPurchasedException.class, DataIntegrityViolationException.class);
// 사전 체크가 잡았거나 DB UNIQUE 가 잡았거나 — 두 가능 경로만 허용
```

**교훈**: 동시성 IT 에서 패자의 예외 타입은 **구체적으로** 검증. "어떤 race 가드가 잡았는지" 두 가능 경로를 명시. 그 외 예외면 race 보호가 깨진 것.

---

### F.3 단일 스레드 IT 는 `@Transactional`, 동시성 IT 는 `TransactionTemplate` — ✅

**출처**: PR #74 / PR #80 / PR #82

**전 (잘못된 셋업)**:
```java
@Transactional
class ConcurrencyIT extends AbstractIntegrationTest {
    @Test
    void concurrent() {
        Long id = setupRow();  // 같은 트랜잭션 — 워커 스레드가 못 봄
        // 두 스레드 실행 → 둘 다 row 없다고 실패
    }
}
```

**후**:
```java
// 클래스 레벨 @Transactional 없음
class ConcurrencyIT extends AbstractIntegrationTest {
    @Autowired private TransactionTemplate tx;

    @Test
    void concurrent() {
        Long id = tx.execute(s -> setupRow());  // 명시적 commit
        // 두 스레드가 row 를 본다
    }
}
```

**교훈**: 동시성 IT 는 클래스 레벨 `@Transactional` 빼고 `TransactionTemplate` 으로 셋업 row 를 명시 commit. 단일 스레드 정합성 IT 는 `@Transactional` 유지해서 격리.

---

### F.4 컨트롤러 IT 에서 동시성 중복 검증 안 함 — ✅

**출처**: 메모리 `feedback_test_layering.md` / PR #80, #82

**판단**: CodeRabbit 가 controller IT 에도 동시성 케이스 추가 요청 — 거절. 동시성은 service IT 에서 검증, controller IT 는 HTTP 봉투 / 인증 / 401·403·404 / 입력 검증에 집중.

**교훈**: 테스트 레이어 분담을 명확히 — service IT (트랜잭션·동시성), controller IT (HTTP·auth·DTO). 같은 시나리오를 두 레이어에서 검증하면 유지보수 비용만 ↑, 시그널은 비슷.

---

## H. 외부 호출 / 통합

WebClient / Resilience4j / ports-adapter 같은 외부 시스템 호출 어댑터 패턴에서의 함정.

### H.1 application.yml 의 설정값이 코드에서 실제 사용되는지 확인 — ✅

**출처**: PR #84

**지적**: `OpenAiProperties.embedding.timeout` 을 application.yml 에 정의하고 record 에 바인딩해두었는데, 어댑터 코드는 그 값을 어디에도 쓰지 않았다. 결과: 외부 응답이 늦어도 `.block()` 이 무한 대기. Resilience4j 재시도/서킷 가드도 작동 못 함 (예외/타임아웃이 안 발생하므로 트리거되지 않는다).

**전**:
```java
public OpenAiEmbeddingAdapter(WebClient.Builder builder, OpenAiProperties props) {
    this.webClient = builder
            .baseUrl(props.embedding().baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
            .build();
    // props.embedding().timeout() 은 어디에도 안 쓰임 — 시그널 없음
}
```

**후**:
```java
public OpenAiEmbeddingAdapter(WebClient.Builder builder, OpenAiProperties props) {
    HttpClient httpClient = HttpClient.create()
            .responseTimeout(props.embedding().timeout());
    this.webClient = builder
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl(props.embedding().baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
            .build();
}
```

**판단 근거**: 진짜 버그. yml 에 박힌 timeout 이 실제 적용 안 되면 외부 시스템이 묶일 때 우리도 같이 묶인다. backend/CLAUDE.md §"외부 호출" 도 "WebClient + WebClientCustomizer로 타임아웃 30초" 라고 명시했지만 자바에 연결을 놓쳤다.

**교훈**: 외부 호출 어댑터 작성 시 self-check — application.yml 에 박은 설정값이 코드 어디서 실제로 쓰이는지 추적. ConfigurationProperties 만 정의하고 인젝션만 받으면 "사용한 것 같은" 착각 일으킴. WebClient 의 timeout 은 `WebClient.Builder` 자체엔 옵션 없음 — Reactor Netty `HttpClient.responseTimeout` + `ReactorClientHttpConnector` 로 연결해야 한다. 비슷한 함정 (caffeine TTL, JWT issuer 등) 도 동일하게 점검.

---

### H.2 Resilience4j retry-exceptions 의 inner class 표기 회피 — ✅

**출처**: PR #84

**지적**: `WebClientResponseException$TooManyRequests` 같은 정적 inner class 의 binary name (`$` 표기) 은 Spring Boot YAML ConfigurationProperties 바인딩에서 fragile. 환경/라이브러리 버전에 따라 `Class.forName` 이 실패해 retry 가드가 조용히 작동 안 할 수 있다.

**전**:
```yaml
resilience4j:
  retry:
    instances:
      openai-embedding:
        retry-exceptions:
          - org.springframework.web.reactive.function.client.WebClientResponseException$TooManyRequests
          - org.springframework.web.reactive.function.client.WebClientResponseException$ServiceUnavailable
          - java.util.concurrent.TimeoutException
```

**후**:
```yaml
resilience4j:
  retry:
    instances:
      openai-embedding:
        retry-exceptions:
          - org.springframework.web.reactive.function.client.WebClientResponseException
          - java.util.concurrent.TimeoutException
```

**판단 근거**: base class 로 단순화. 4xx (401 인증 실패 등) 도 함께 3 회 재시도되지만 임베딩처럼 부가 기능 (실패 swallow) 이면 비용 미미. 정확한 분류가 필요한 경우엔 코드 레벨에서 `retryExceptionPredicate` 빈 + 분류 또는 wrapper exception 도입 — 이번 PR 은 단순화로 충분.

**교훈**: YAML 의 클래스 이름 바인딩은 **base class / 외부 노출 public class 만** 사용. inner class 의 `$` 표기는 피한다. 다중 sealed-style 분류가 필요하면:
- (a) 부가 기능 — 그냥 base class + 영구 에러도 N 회 재시도 허용 (비용 < 정확성)
- (b) 핵심 트랜잭션 — `retryExceptionPredicate` 빈 정의 + 어댑터에서 wrapper exception 던지기 (정확성 > 비용)

---

## G. 의도적으로 안 받은 지적

각각 한 줄 사유. 같은 지적이 또 오면 이 섹션 링크 답글로 갈음.

### G.1 도메인 엔티티 PK 를 UUID 로 강제 — ❌
CLAUDE.md §10.1: PK 는 `bigserial(Long)`, `users` 만 uuid (Supabase auth.users.id 매핑 위한 예외). bigserial 이 인덱스 크기 / 시퀀스 성능에서 우위.

### G.2 마크다운 린트 (MD040, 코드 펜스 언어) — ❌
렌더링 차이 없음 + CI 에 markdownlint 안 걸려있음. 도입할 가치 없는 노이즈.

### G.3 actor UUID 를 에러 메시지에서 빼기 — ❌
caller 본인 UUID 는 정보 노출 아님. UX 차원에서 정리 marginal — 비용 < 가치.

### G.4 `@ResponseStatus` 를 web 레이어 별 advice 로 분리 → 전역 advice 로 — ⏳
도메인별 분리가 더 깔끔하다는 판단 ([D.3](#d3-도메인별-advice-패키지-격리-)). 향후 공유 예외가 늘면 재검토.

### G.5 `controller IT` 동시성 케이스 — ❌
[F.4](#f4-컨트롤러-it-에서-동시성-중복-검증-안-함-) 참고.

### G.6 application → infrastructure 직접 의존 — ❌
[E.4](#e4-application--infrastructure-직접-의존-허용---의도적), [ADR 0001](adr/0001-package-layout.md).

---

## PR 인덱스

PR 별로 어떤 항목이 나왔는지 — 디버깅 / 머지 후 추적용.

| PR | 주제 | 항목 |
|---|---|---|
| #45 | Flyway V2 + idea 엔티티 | C.2, E.1, F.1 |
| #61 | idea 도메인 가드 | B.1, B.2 |
| #63 | JWT + RBAC 필터 | (별도 fix: JwtAuthenticationConverter 와이어링) |
| #71 | 크레딧 충전 코어 | A.1, A.2, B.4, C.3, C.4, E.2 |
| #73 | 아이디어 구매 | D.4, D.5, F.2 |
| #75 | 구매 API 표준화 | D.1 |
| #77 | IT Fixture 추출 | (구조 정리, lessons 없음) |
| #79 | finalize / 새 버전 | A.3, A.5, B.2 |
| #80 | finalize CodeRabbit 후속 | A.3, A.4 |
| #81 | 채택 + 보상 | B.3, C.1, C.2, D.3 |
| #82 | 채택 CodeRabbit 후속 | B.3, C.1 (V5) |
| #84 | 임베딩 실제 계산 (OpenAI + pgvector, ports/adapter 첫 적용) | H.1, H.2 |
