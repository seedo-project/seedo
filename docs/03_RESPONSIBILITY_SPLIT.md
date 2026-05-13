# 03. 책임 분담 (Spring ↔ Supabase ↔ Next.js)

## 원칙

1. **Supabase가 잘하는 것**: Auth, 단순 CRUD, Storage, Realtime, RLS
2. **Spring이 잘하는 것**: 다중 테이블 트랜잭션, 외부 API 연동, 복잡한 권한, AI 파이프라인
3. **Next.js가 잘하는 것**: SSR, 클라이언트 상태, UI 인터랙션
4. **DB는 단일**: Supabase Postgres 한 곳, Flyway로 일원화 마이그레이션

---

## API 분담표

### 인증 · 프로필

| 엔드포인트 | 담당 | 비고 |
|---|---|---|
| 회원가입 | Supabase Auth | 이메일 인증 포함 |
| 로그인 / 로그아웃 | Supabase Auth | JWT 발급 |
| 비밀번호 재설정 | Supabase Auth | |
| 회원가입 후처리 (user_credits, user_roles 초기화) | **Spring** | webhook 또는 첫 요청 시 lazy init |
| 프로필 조회 | Supabase (직접) | RLS로 본인만 |
| 프로필 수정 | Supabase (직접) | RLS |
| 회원 탈퇴 | **Spring** | 다단계 처리 (보상 정산 + 소프트 삭제) |

### 크레딧

| 엔드포인트 | 담당 | 비고 |
|---|---|---|
| 잔액 조회 | Supabase (직접) | RLS로 본인만 |
| 거래 내역 조회 | Supabase (직접) | RLS |
| 충전 (PG 결제 webhook) | **Spring** | 멱등성 + 트랜잭션 |
| 환불 (관리자) | **Spring** | RBAC + 원장 기록 |

### 아이디어

| 엔드포인트 | 담당 | 비고 |
|---|---|---|
| 챗봇 세션 생성/메시지 | **Spring** | LLM 호출 |
| 챗봇 finalize → idea 생성 | **Spring** | 트랜잭션 |
| 아이디어 발행 (DRAFT → PUBLISHED) | **Spring** | 임베딩 추출 트리거 |
| 아이디어 새 버전 발행 | **Spring** | 트랜잭션 |
| 아이디어 피드 조회 (랜덤/최신) | Supabase (직접) | view 또는 RPC |
| 아이디어 검색 (자연어) | **Spring** | 임베딩 + RAG |
| 아이디어 미리보기 (키워드만) | Supabase (직접) | 권한 체크 RLS |
| 아이디어 구매 | **Spring** | 트랜잭션 (잔액-원장-구매권) |
| 아이디어 본문 조회 | Supabase (직접) | RLS: idea_purchases 또는 본인만 |
| 아이디어 메타 수정 (제목, 카테고리) | Supabase (직접) | RLS로 본인만 |

### 프로젝트

| 엔드포인트 | 담당 | 비고 |
|---|---|---|
| 아이디어 채택 → 프로젝트 생성 | **Spring** | 트랜잭션 (프로젝트+멤버+보상) |
| 프로젝트 직접 등록 | Supabase (직접) 또는 **Spring** | 보상 없으면 단순 CRUD |
| 프로젝트 피드 조회 | Supabase (직접) | |
| 프로젝트 상세 조회 | Supabase (직접) | |
| 프로젝트 상태 변경 | Supabase (직접) | RLS로 leader만 |
| 프로젝트 멤버 가입 신청 | Supabase (직접) | INSERT pending |
| 프로젝트 멤버 승인/거부 | **Spring** 또는 Supabase RPC | 권한 체크 후 status 변경 |

### 인터랙션

| 엔드포인트 | 담당 | 비고 |
|---|---|---|
| Hype 토글 | Supabase (직접) | UNIQUE 제약 + 트리거로 카운트 동기화 |
| Follow 토글 | Supabase (직접) | |
| 댓글 작성/수정/삭제 | Supabase (직접) | RLS로 본인만 |

### 게시판

| 엔드포인트 | 담당 | 비고 |
|---|---|---|
| 게시물 작성/수정/삭제 | Supabase (직접) | RLS |
| 게시물 조회 (필터, 검색) | Supabase (직접) | |
| 모집 지원 | Supabase (직접) | |
| 지원 수락/거절 | Supabase (직접) | RLS로 작성자만 |

### 검색

| 엔드포인트 | 담당 | 비고 |
|---|---|---|
| 자연어 아이디어 검색 (RAG) | **Spring** | 쿼리 임베딩 + pgvector |
| 키워드 아이디어 검색 | Supabase RPC 또는 **Spring** | gin 인덱스 활용 |
| 게시판 키워드 검색 | Supabase (직접) | 단순 ILIKE 또는 FTS |

### 관리자

| 엔드포인트 | 담당 | 비고 |
|---|---|---|
| 사용자 정지/해제 | **Spring** | RBAC ADMIN 권한 체크 |
| 콘텐츠 강제 삭제 | **Spring** | RBAC + 감사 로그 |
| 크레딧 환불 | **Spring** | RBAC + 원장 기록 |
| 통계 대시보드 | **Spring** | 복잡 쿼리 |

---

## RLS 정책 가이드라인

### 기본 원칙
1. RLS는 **1차 방어선**. Spring 우회 경로(service_role)에선 적용 안 됨.
2. 본인 데이터는 본인만 (가장 흔함):
   ```sql
   CREATE POLICY user_credits_self ON user_credits
     FOR SELECT USING (user_id = auth.uid());
   ```
3. 작성자만 수정:
   ```sql
   CREATE POLICY ideas_owner_update ON ideas
     FOR UPDATE USING (author_id = auth.uid());
   ```
4. 공개 데이터 모두 읽기 가능:
   ```sql
   CREATE POLICY ideas_published_read ON ideas
     FOR SELECT USING (status = 'PUBLISHED' AND deleted_at IS NULL);
   ```
5. 구매한 사람만 본문 조회:
   ```sql
   CREATE POLICY idea_documents_purchased ON idea_documents
     FOR SELECT USING (
       EXISTS (SELECT 1 FROM idea_purchases
               WHERE idea_id = idea_documents.idea_id
                 AND buyer_id = auth.uid())
       OR EXISTS (SELECT 1 FROM ideas
                  WHERE id = idea_documents.idea_id
                    AND author_id = auth.uid())
     );
   ```

### Spring이 우회할 수 있는 이유
- `service_role` 키는 RLS 우회. Spring은 비즈니스 로직 안에서 직접 권한 검증.
- 클라이언트(Next.js)는 `anon` 키 + 사용자 JWT 사용 → RLS 적용됨.

---

## Next.js 클라이언트 측 패턴

### Supabase 직접 호출
```typescript
// 단순 조회는 supabase-js 직결
const { data } = await supabase
  .from('ideas')
  .select('id, title, summary, hype_count')
  .eq('status', 'PUBLISHED')
  .order('published_at', { ascending: false })
  .limit(20);
```

### Spring API 호출
```typescript
// 트랜잭션이 필요한 작업은 Spring 경유
const res = await fetch('/api/spring/ideas/123/purchase', {
  method: 'POST',
  headers: { Authorization: `Bearer ${session.access_token}` },
});
```

Spring API는 별도 도메인(예: `api.seedo.dev`)에 두거나, Next.js의 `/api/*` 라우트가 Spring으로 프록시.

---

## 공유 인증 흐름

```
[브라우저] ── login ──▶ [Supabase Auth] ── JWT ──▶ [브라우저]
                                                      │
                                  ┌──── JWT ────────┤
                                  ▼                  ▼
                            [Spring API]      [Supabase REST]
                                  │                  │
                                  │ JWKS verify      │ RLS evaluate
                                  ▼                  ▼
                              [Postgres] ◀── 동일 DB ──┘
```

- 동일 JWT가 두 백엔드에서 모두 통한다.
- Spring은 JWKS로 서명 검증, Supabase REST는 자체적으로 JWT 처리.
- 사용자 입장에선 한 번 로그인이 양쪽 다 적용.

---

## 구현 순서 제안

**Phase 1: 인프라 + 인증**
1. Supabase 프로젝트 생성, Postgres 확장 활성화 (vector, pgcrypto)
2. Spring Boot 프로젝트 부트스트랩, Flyway 설정
3. Flyway V1__init.sql — RBAC, users, user_credits 마이그레이션
4. Supabase Auth 연동 (Next.js 회원가입/로그인)
5. Spring JWT 검증 필터 + Spring Security 설정

**Phase 2: 크레딧 + 아이디어**
6. 크레딧 충전 API (PG 모킹) + 충전 webhook
7. 아이디어 챗봇 세션/메시지 API + LLM 연동
8. 아이디어 finalize + 발행 API
9. 임베딩 추출 + 검색 API
10. 아이디어 구매 API (트랜잭션)

**Phase 3: 프로젝트 + 보상**
11. 프로젝트 채택 트랜잭션 (Spring)
12. 프로젝트 피드 (Supabase 직결)
13. 멤버 관리

**Phase 4: 게시판 + 인터랙션**
14. 게시판 CRUD (Supabase 직결)
15. 모집 지원 (Supabase 직결)
16. Hype, Follow, 댓글 (Supabase 직결)

**Phase 5: 마무리**
17. 알림 (DB 트리거 + Realtime)
18. 관리자 대시보드 (Spring)
19. 모니터링, 로깅, 배포

---

## 현재 진행 상태

이 문서는 **설계 원칙** 이다. **실제로 머지된 것** 의 한 눈 정리는 [`07_IMPLEMENTATION_STATUS.md`](./07_IMPLEMENTATION_STATUS.md) 참조 — Flyway V1~V18, 컨트롤러 / 서비스 / 어댑터 목록, RLS 정책, view, PR 인덱스, 미구현·보류 항목 모두 거기 있다.
