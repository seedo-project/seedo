# Seedo — Project Context

> 이 문서는 Seedo 프로젝트의 전체 컨텍스트입니다. AI assistant가 작업을 이어받을 때 이 문서 + 02번부터 04번 문서를 함께 읽으면 됩니다.

## 1. 프로젝트 한 줄 요약

**Seedo**는 일상 속 니즈를 가진 사용자(소비자)와 프로덕트 개발자를 연결하는 **Pain Point 마켓플레이스 + 프로젝트 크라우드펀딩** 플랫폼이다. 사용자는 AI 챗봇과 대화하며 자신의 니즈를 정형화된 기획문서로 만들고, 개발자는 크레딧을 지불하고 열람·채택할 수 있다. 채택 시 사용자에게 크레딧 보상이 돌아가며, 채택된 아이디어는 프로젝트로 전환되어 진행 상황을 공유하고 베타테스터 모집 등을 할 수 있다.

## 2. 핵심 도메인 모델

```
User ──작성──▶ Idea ──채택──▶ Project
  │             │              │
  │             │              ├─ Members
  │             │              ├─ Followers
  │             │              └─ Hypes
  │             │
  │             ├─ Documents (versioned)
  │             ├─ ChatSessions (AI 대화 로그)
  │             ├─ Embeddings (검색용)
  │             └─ Purchases (열람권)
  │
  ├─ Posts ──── Applications (베타테스터/개발자 모집)
  ├─ Credits (잔액 + 원장)
  └─ Roles (RBAC)
```

## 3. 핵심 비즈니스 규칙

### 3.1 크레딧

- 모든 크레딧 이동은 `credit_transactions` 원장에 기록된다 (단일 진실 공급원).
- `user_credits.balance`는 캐시이며, 원장으로 검증 가능해야 한다.
- 크레딧 이동 트랜잭션 타입: `CHARGE`(충전), `SPEND`(아이디어 열람·인터뷰 보상 등 차감), `REWARD`(보상 수령), `REFUND`(환불).
- **원자성**: 잔액 차감 + 거래 기록 + 권한 부여(예: 열람권)는 항상 같은 트랜잭션 안에서 처리한다.
- 동시성: 같은 사용자가 동시에 여러 결제 요청 시 비관적 락(`SELECT FOR UPDATE`)으로 직렬화한다.

### 3.2 아이디어 버저닝과 열람권

- `ideas`는 메타 정보, `idea_documents`는 실제 본문(버전별 row).
- `ideas.current_version_id`는 published된 최신 버전을 가리킨다.
- **published 후 본인이 수정해도 새 버전 row가 생성**될 뿐, 기존 버전은 그대로 보존된다.
- 구매자는 자신이 산 시점의 `document_id`(스냅샷)를 영구히 볼 권한이 있다 (분쟁 방지).
- 미리보기는 키워드 3~5개만 노출. 전체 문서는 크레딧 결제 후에만 열람 가능.

### 3.3 아이디어 → 프로젝트 채택

- 개발자가 채택 시 다음이 한 트랜잭션 안에서 이뤄져야 한다:
  1. `projects` row 생성
  2. `projects.idea_snapshot_md`에 채택 시점 idea 본문 복사 (idea가 후에 삭제돼도 프로젝트 보존)
  3. `rewards` 발행 → `credit_transactions`로 보상 지급 → `user_credits.balance` 갱신
- 한 아이디어가 여러 프로젝트로 채택될 수 있는지는 정책 결정 필요. **MVP에선 1:N 허용**(여러 개발자가 같은 아이디어로 별도 프로젝트 가능)으로 시작.

### 3.4 권한 (RBAC + 리소스 소유권)

- **시스템 전역 역할**: `USER`, `ADMIN` (테이블: `roles` + `user_roles` + `permissions` + `role_permissions`).
- **리소스 소유권**: "본인 글 수정"같은 건 RBAC 외부 로직(`@PreAuthorize("@ideaSecurity.isOwner(...)")`)으로 처리.
- **RLS는 1차 방어선**: Supabase에서 RLS는 "본인 데이터만 접근 가능" 수준으로만. 본격적인 RBAC 검증은 Spring에서.

### 3.5 Hype vs Follow

- **Hype**: 1회성 응원 (좋아요와 비슷하나 취소 불가 또는 일정 시간 후 다시 가능 — 정책 미정).
- **Follow**: 구독. 새 글/상태 변화 알림 받음.
- 두 개념은 별개 테이블.

### 3.6 댓글

- `idea_comments`, `project_comments`, `post_comments` — 도메인별로 분리된 3테이블.
- `parent_id`로 1단계 대댓글 지원.
- Spring에선 `@MappedSuperclass`로 BaseComment 추출, 도메인별 엔티티가 상속.

## 4. 화면 ↔ 도메인 매핑 (와이어프레임 기준)

| 화면 | 라우트 | 주요 테이블 |
|---|---|---|
| 로그인 | `/` | `users`, Supabase Auth |
| 회원가입 | `/sign-up` | `users`, `user_roles`, `user_credits` (초기 0) |
| 비밀번호 찾기 | `/find-password` | Supabase Auth |
| 아이디어 피드 | `/idea` | `ideas`, `idea_embeddings` (검색) |
| 아이디어 작성 | `/idea-upload` | `idea_chat_sessions`, `idea_chat_messages`, `ideas`, `idea_documents` |
| 아이디어 상세 | `/idea-page?id=` | `ideas`, `idea_documents`, `idea_purchases`, `credit_transactions` |
| 프로젝트 피드 | `/feed` | `projects`, `hypes`, `project_follows` |
| 프로젝트 등록 | `/project-upload` | `projects`, `rewards`, `credit_transactions` |
| 프로젝트 상세 | `/feed-page?id=` | `projects`, `project_members`, `project_comments` |
| 게시판 | `/board` | `posts` |
| 게시물 작성 | `/board-upload` | `posts` |
| 게시물 상세 | `/board-page?id=` | `posts`, `post_comments`, `post_applications` |
| 마이페이지 | `/my-page` | `users`, `user_credits`, 본인 작성물 모음 |

## 5. 결정된 아키텍처 원칙

1. **모놀리스 분담**: Next.js (FE) ↔ Spring Boot (핵심 비즈니스) + Supabase (Auth/CRUD/Storage/RLS).
2. **단일 DB**: Supabase Postgres를 두 백엔드가 공유.
3. **마이그레이션 일원화**: Flyway만 사용. Supabase Studio에서 직접 스키마 변경 금지 (팀 룰).
4. **Spring은 service_role 키**로 DB 접근 → RLS 우회, 권한은 Spring에서 검증.
5. **JWT 검증**: Supabase Auth가 발급한 JWT를 Spring Security 커스텀 필터로 검증 (JWKS 기반).
6. **소프트 삭제 우선**: `deleted_at` 컬럼 활용. hard delete는 ADMIN만.
7. **USERS 동기화**: `users.id = auth.users.id` (UUID 공유), DB 트리거 `handle_new_user()`로 자동 생성.
8. **아이디어 버저닝**: published 후 새 버전 추가 가능, 기존 구매자는 무료 업그레이드.
9. **AI 스택**: Claude Haiku 4.5 (대화 턴) + Sonnet 4.6 (finalize) + OpenAI text-embedding-3-small (검색).
10. **모노레포**: 한 GitHub 레포, `backend/` + `web/` + `docs/` 폴더 분리, Flyway는 `backend/src/main/resources/db/migration/`.

## 6. 추후 결정 사항

상세는 `04_OPEN_QUESTIONS.md` 참조. 주요 미결정:

- [ ] Hype 1회성 vs 토글 (권장: 토글)
- [ ] 한 아이디어 → 여러 프로젝트 허용 여부 (MVP: 허용, 보상은 첫 채택자만)
- [ ] 크레딧 충전 결제 PG 연동 (MVP는 무료 크레딧, 추후 PortOne)
- [ ] 알림 채널 (MVP: 인앱 + Realtime)
- [ ] 프로젝트 상태 머신 (`draft` / `recruiting` / `in_progress` / `completed` / `archived`)
- [ ] 아이디어 카테고리 enum 확정
- [ ] 아이디어 가격 책정 (MVP: 플랫폼 고정가)
- [ ] 채택 보상 금액 (MVP: 가격의 50% 등 고정)

## 7. 다음 작업 순서 (제안)

1. Supabase 프로젝트 생성, Spring Boot 프로젝트 부트스트랩
2. Flyway 설정 + V1 마이그레이션 (USERS, ROLES, PERMISSIONS, USER_CREDITS, CREDIT_TRANSACTIONS)
3. Supabase JWT 검증 필터 + Spring Security 설정
4. 회원가입 플로우 end-to-end (Supabase Auth signup → Spring webhook → user_roles + user_credits 초기화)
5. 크레딧 충전/차감 API + 동시성 테스트
6. 아이디어 CRUD + 챗봇 연동
7. 임베딩 추출 + 검색 API
8. 아이디어 구매 플로우 (트랜잭션 원자성)
9. 프로젝트·게시판·댓글
10. 보상 정산 자동화

## 8. 참고 문서

- `01_DB_SCHEMA.md` — 테이블 상세 명세 (컬럼, 제약, 인덱스, FK 정책)
- `02_BUSINESS_LOGIC.md` — 핵심 트랜잭션 시퀀스 (크레딧, 구매, 채택)
- `03_RESPONSIBILITY_SPLIT.md` — Spring vs Supabase API 분담표
- `04_OPEN_QUESTIONS.md` — 미결정 사항 상세
