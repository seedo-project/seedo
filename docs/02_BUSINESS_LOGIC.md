# 02. 비즈니스 로직 시퀀스

> Spring `@Transactional`로 처리해야 하는 핵심 트랜잭션 단위 시나리오. 각 시나리오는 **원자성**이 중요한 부분이다.

## 1. 회원가입 후처리

**트리거**: Supabase Auth signup 완료 webhook (또는 Spring 내부에서 Supabase Auth API 호출)

```
BEGIN
  INSERT INTO users (id, email, nickname, real_name, birthday, gender)
    VALUES (auth_user_id, ..., ...);
  INSERT INTO user_credits (user_id, balance) VALUES (auth_user_id, 0);
  INSERT INTO user_roles (user_id, role_id, assigned_by)
    VALUES (auth_user_id, (SELECT id FROM roles WHERE code='USER'), NULL);
COMMIT
```

**주의**
- Supabase Auth가 먼저 생성된 `auth.users.id`를 기반으로 동기화. 동기화 실패 대비 retry 큐 필요.
- `user_credits` 초기 잔액은 0. 신규 가입 보너스 정책이 있다면 별도 `CREDIT_TRANSACTIONS` row 추가 (type='ADJUST', reference_type='SIGNUP_BONUS').

---

## 2. 크레딧 충전

**트리거**: PG 결제 완료 webhook

```
BEGIN
  -- 1. 멱등성 체크: 같은 PG 주문번호로 이미 처리됐으면 skip
  SELECT 1 FROM credit_transactions
    WHERE reference_type='PG_PAYMENT' AND reference_id=:payment_id;
  IF EXISTS RETURN;

  -- 2. 잔액 락 + 갱신
  SELECT balance FROM user_credits WHERE user_id=:user_id FOR UPDATE;
  UPDATE user_credits
    SET balance = balance + :amount, updated_at = now()
    WHERE user_id = :user_id;

  -- 3. 원장 기록
  INSERT INTO credit_transactions
    (user_id, amount, type, reference_type, reference_id, balance_after, memo)
  VALUES
    (:user_id, :amount, 'CHARGE', 'PG_PAYMENT', :payment_id, :new_balance, '크레딧 충전');
COMMIT
```

**Spring 구현 포인트**
- `@Transactional(isolation=READ_COMMITTED)` + 비관적 락(`SELECT FOR UPDATE`)
- 멱등성 키: `(reference_type, reference_id)` UNIQUE 인덱스 또는 사전 SELECT
- PG webhook 재시도 대응 — 같은 결제 두 번 들어와도 한 번만 처리

---

## 3. 아이디어 구매 (열람)

**플로우**

1. 사용자가 idea 상세 페이지 진입 → 모달 "X 크레딧을 지불하시겠습니까?"
2. 확인 시 아래 트랜잭션 실행

```
BEGIN
  -- 1. 이미 산 적 있으면 즉시 권한 부여하고 종료
  SELECT 1 FROM idea_purchases WHERE idea_id=:idea_id AND buyer_id=:buyer_id;
  IF EXISTS RETURN '이미 구매함';

  -- 2. idea 상태 검증 (PUBLISHED 만 구매 가능)
  SELECT status, current_version_id, price_credits FROM ideas
    WHERE id=:idea_id FOR UPDATE;
  IF status != 'PUBLISHED' RAISE '구매 불가';

  -- 3. 자기 아이디어는 못 사게
  IF author_id = buyer_id RAISE '본인 아이디어';

  -- 4. 잔액 락 + 검증
  SELECT balance FROM user_credits WHERE user_id=:buyer_id FOR UPDATE;
  IF balance < price_credits RAISE '잔액 부족';

  -- 5. 잔액 차감
  UPDATE user_credits
    SET balance = balance - :price_credits, updated_at = now()
    WHERE user_id = :buyer_id;

  -- 6. 원장 기록 (차감)
  INSERT INTO credit_transactions
    (user_id, amount, type, reference_type, reference_id, balance_after, memo)
  VALUES
    (:buyer_id, -:price_credits, 'SPEND', 'IDEA_PURCHASE',
     :purchase_id_placeholder, :new_balance, '아이디어 구매')
  RETURNING id;  -- :tx_id

  -- 7. 구매권 생성 (산 시점 document_id 스냅샷)
  INSERT INTO idea_purchases
    (idea_id, document_id, buyer_id, credits_paid, transaction_id)
  VALUES
    (:idea_id, :current_version_id, :buyer_id, :price_credits, :tx_id);
COMMIT
```

**경합 시나리오**
- 같은 사용자가 잔액 100, 100짜리 아이디어 두 개를 동시 결제 시도 → `user_credits` 락이 직렬화하므로 한 건만 성공.
- 같은 아이디어를 같은 사용자가 동시에 두 번 클릭 → `UNIQUE(idea_id, buyer_id)`로 두 번째 INSERT 실패. 두 번째 트랜잭션은 롤백 후 "이미 구매함" 응답.

---

## 4. 아이디어 채택 → 프로젝트 생성 + 보상

**가장 복잡한 트랜잭션**. 여러 도메인 동시 변경.

```
BEGIN
  -- 1. 아이디어 검증
  SELECT status, current_version_id, author_id, reward_credits
    FROM ideas WHERE id=:idea_id FOR UPDATE;
  IF status != 'PUBLISHED' RAISE;

  -- 2. 채택자가 산 적 있는지 확인 (정책: 산 사람만 채택 가능)
  SELECT 1 FROM idea_purchases
    WHERE idea_id=:idea_id AND buyer_id=:adopter_id;
  IF NOT EXISTS RAISE '먼저 구매 필요';

  -- 3. 프로젝트 생성 (idea 본문 스냅샷 포함)
  SELECT content_md FROM idea_documents WHERE id=:current_version_id INTO :snapshot_md;

  INSERT INTO projects
    (idea_id, leader_id, title, summary, status,
     idea_snapshot_md, idea_snapshot_at, started_at)
  VALUES
    (:idea_id, :adopter_id, :title, :summary, 'DRAFT',
     :snapshot_md, now(), now())
  RETURNING id;  -- :project_id

  -- 4. 리더를 멤버로 등록
  INSERT INTO project_members
    (project_id, user_id, project_role, status, joined_at)
  VALUES
    (:project_id, :adopter_id, 'LEADER', 'ACTIVE', now());

  -- 5. 작성자에게 보상 지급
  -- 5-1. 잔액 락 + 갱신
  SELECT balance FROM user_credits
    WHERE user_id=:idea_author_id FOR UPDATE;
  UPDATE user_credits
    SET balance = balance + :reward_credits, updated_at = now()
    WHERE user_id = :idea_author_id;

  -- 5-2. 원장 기록
  INSERT INTO credit_transactions
    (user_id, amount, type, reference_type, reference_id, balance_after, memo)
  VALUES
    (:idea_author_id, :reward_credits, 'REWARD',
     'REWARD', :reward_id_placeholder, :new_balance, '아이디어 채택 보상')
  RETURNING id;  -- :tx_id

  -- 5-3. rewards 메타 기록
  INSERT INTO rewards
    (idea_id, project_id, recipient_user_id, created_by,
     reward_type, amount, status, transaction_id, approved_at, paid_at)
  VALUES
    (:idea_id, :project_id, :idea_author_id, :adopter_id,
     'ADOPTION', :reward_credits, 'PAID', :tx_id, now(), now());
COMMIT
```

**대안: 보상 비동기 처리**
- 위처럼 한 트랜잭션에 다 묶으면 트랜잭션이 길어짐. 락 보유 시간 길어지면 성능 저하.
- 도메인 이벤트 패턴: `ProjectCreatedEvent` 발행 → 별도 핸들러에서 보상 처리
- `@TransactionalEventListener(phase=AFTER_COMMIT)`로 프로젝트 생성 후 보상 트랜잭션 별도 실행
- 트레이드오프: 트랜잭션 짧아지지만 일관성은 eventual

**MVP 권장**: 일단 한 트랜잭션. 트래픽 늘면 분리.

---

## 5. AI 에이전트 대화 → 아이디어 발행

**플로우**

1. `POST /api/idea-chat/sessions` → 새 세션 생성, 첫 시스템 메시지 삽입
2. 사용자가 메시지 전송 → `POST /api/idea-chat/sessions/{id}/messages`
   - 메시지 저장, LLM 호출, 응답 저장
   - LLM 응답에 "충분한 정보 수집 완료" 시그널이 있으면 finalize 단계로
3. Finalize:
   ```
   BEGIN
     -- 1. ideas 메타 생성 (DRAFT 상태)
     INSERT INTO ideas (author_id, title, summary, category, status)
       VALUES (:user_id, :title, :summary, :category, 'DRAFT')
     RETURNING id;  -- :idea_id

     -- 2. 첫 버전 문서 생성
     INSERT INTO idea_documents (idea_id, version, content_md, created_by)
       VALUES (:idea_id, 1, :content_md, :user_id)
     RETURNING id;  -- :doc_id

     -- 3. 메타에 current_version_id 연결
     UPDATE ideas SET current_version_id = :doc_id WHERE id = :idea_id;

     -- 4. 세션 마무리
     UPDATE idea_chat_sessions
       SET idea_id = :idea_id, status = 'FINALIZED', finalized_at = now()
       WHERE id = :session_id;
   COMMIT
   ```
4. 비동기로 임베딩 추출 + 키워드 추출 → `idea_embeddings` upsert
5. 사용자가 "발행" 버튼 클릭 시 status를 'PUBLISHED'로 변경 + `published_at` 기록

**LLM 호출 정책**
- 타임아웃 30초
- Resilience4j 서킷 브레이커: 5회 연속 실패 시 OPEN, 60초 후 HALF_OPEN
- 재시도: 일시적 오류(429, 503)만 3회 지수 백오프

---

## 6. 아이디어 새 버전 발행

작성자가 본인 아이디어를 수정해서 새 버전을 만드는 시나리오.

```
BEGIN
  -- 1. 권한 체크 (본인만)
  SELECT author_id FROM ideas WHERE id=:idea_id;
  IF author_id != :user_id RAISE;

  -- 2. 다음 version 번호 계산
  SELECT COALESCE(MAX(version), 0) + 1 FROM idea_documents
    WHERE idea_id = :idea_id INTO :next_version;

  -- 3. 새 버전 row
  INSERT INTO idea_documents
    (idea_id, version, content_md, attachment_urls, created_by, change_note)
  VALUES
    (:idea_id, :next_version, :content_md, :attachments, :user_id, :change_note)
  RETURNING id;  -- :new_doc_id

  -- 4. current_version_id 갱신
  UPDATE ideas
    SET current_version_id = :new_doc_id, updated_at = now()
    WHERE id = :idea_id;
COMMIT

-- 비동기: 임베딩 재추출
```

**기존 구매자 정책 (MVP 기준)**
- 새 버전 발행 후에도 기존 구매자는 새 버전을 무료로 볼 수 있게 정책 결정 (`idea_purchases.document_id`는 산 시점 추적용으로만, 접근 권한은 idea_id 기준).
- 분쟁 방지를 위한 스냅샷 보존: 산 시점 `document_id`는 영구 기록.

---

## 7. 베타테스터 모집 지원 → 수락

```
-- 지원
INSERT INTO post_applications (post_id, applicant_id, message, status)
  VALUES (:post_id, :user_id, :message, 'PENDING');

-- 작성자가 수락
UPDATE post_applications
  SET status='ACCEPTED', responded_at=now()
  WHERE id=:application_id AND post_id IN
    (SELECT id FROM posts WHERE author_id=:current_user_id);

-- 알림 발송 (별도 트랜잭션 또는 이벤트)
INSERT INTO notifications (user_id, type, reference_type, reference_id, payload)
  VALUES (:applicant_id, 'APPLICATION_ACCEPTED', 'POST', :post_id, '{...}');
```

---

## 8. Hype 토글 (정책 미정)

**옵션 A: 1회성 (취소 불가)**
- 단순 INSERT, UNIQUE 제약으로 중복 방지

**옵션 B: 토글**
```
INSERT INTO hypes (user_id, idea_id) VALUES (...)
ON CONFLICT (user_id, idea_id) DO NOTHING;
-- 또는
DELETE FROM hypes WHERE user_id=:u AND idea_id=:i;
```

**hype_count 동기화**: 트리거로 `ideas.hype_count` 자동 갱신
```sql
CREATE OR REPLACE FUNCTION update_idea_hype_count() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'INSERT' AND NEW.idea_id IS NOT NULL THEN
    UPDATE ideas SET hype_count = hype_count + 1 WHERE id = NEW.idea_id;
  ELSIF TG_OP = 'DELETE' AND OLD.idea_id IS NOT NULL THEN
    UPDATE ideas SET hype_count = hype_count - 1 WHERE id = OLD.idea_id;
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;
```

---

## 9. RBAC 권한 체크 (Spring Security)

### 9.1 시스템 권한 (`@PreAuthorize`)

```java
@PreAuthorize("hasAuthority('PERM_CREDIT_REFUND')")
public void refund(...) { ... }
```

### 9.2 리소스 소유권 체크

```java
@Component("ideaSecurity")
public class IdeaSecurityService {
    public boolean isOwner(Long ideaId, Authentication auth) {
        UUID userId = ((CustomPrincipal) auth.getPrincipal()).getId();
        return ideaRepository.existsByIdAndAuthorId(ideaId, userId);
    }
}

// 사용
@PreAuthorize("@ideaSecurity.isOwner(#ideaId, authentication)")
public void updateIdea(@PathVariable Long ideaId, ...) { ... }
```

### 9.3 권한 로딩 (UserDetailsService)

```
SELECT p.code FROM users u
  JOIN user_roles ur ON ur.user_id = u.id
  JOIN role_permissions rp ON rp.role_id = ur.role_id
  JOIN permissions p ON p.id = rp.permission_id
  WHERE u.id = :userId
```

각 permission code를 `SimpleGrantedAuthority("PERM_" + code)`로 변환. 결과는 Caffeine 캐시(TTL 5분).

---

## 10. JWT 검증 필터 (Supabase Auth)

```
1. Authorization 헤더에서 Bearer 토큰 추출
2. Supabase JWKS 엔드포인트로 공개키 fetch (캐싱)
3. 서명 검증 + exp 검증
4. claim에서 sub(=user_id) 추출
5. user_id로 users 테이블 조회 + 권한 로드
6. SecurityContext에 인증 정보 set
```

**구현**
- `nimbus-jose-jwt` 또는 `jjwt` 라이브러리
- JWKS: `https://<project>.supabase.co/auth/v1/.well-known/jwks.json`
- `OncePerRequestFilter` 상속한 커스텀 필터

---

## 11. 트랜잭션 정합성 체크리스트

각 신규 API 구현 시 확인:

- [ ] 잔액 변경이 있다면 `user_credits` 락 + `credit_transactions` 추가가 같은 트랜잭션 안에 있는가
- [ ] UNIQUE 제약 위반 시 적절한 비즈니스 에러로 변환되는가
- [ ] 외부 API 호출(LLM, PG)이 트랜잭션 안에 있다면 짧은 타임아웃 + 보상 트랜잭션 패턴 고려
- [ ] 멱등성 키가 필요한 webhook은 사전 SELECT 또는 UNIQUE 인덱스로 보호
- [ ] 권한 체크가 비즈니스 로직 진입 전에 이뤄지는가
- [ ] 동시 요청에 대한 락 전략이 명확한가 (낙관적 vs 비관적)
