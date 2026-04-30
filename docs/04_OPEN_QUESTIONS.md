# 04. 결정사항 & 미결정 사항

> 우선순위 4개는 결정됨. 나머지는 구현하면서 채워나간다.

---

## ✅ 결정된 사항 (구현 시 이 결정을 따른다)

### ✅ B.1 USERS와 Supabase auth.users 관계 — **옵션 A**

**결정**: `users.id` = `auth.users.id` (UUID 공유)

```sql
CREATE TABLE users (
  id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email varchar(255) UNIQUE NOT NULL,
  nickname varchar(50) NOT NULL,
  ...
);
```

**동기화 방법**: DB 트리거 (Supabase 공식 패턴)

```sql
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS trigger AS $$
BEGIN
  INSERT INTO public.users (id, email, nickname)
    VALUES (
      NEW.id,
      NEW.email,
      COALESCE(NEW.raw_user_meta_data->>'nickname', 'user_' || substr(NEW.id::text, 1, 8))
    );

  INSERT INTO public.user_credits (user_id, balance)
    VALUES (NEW.id, 0);

  INSERT INTO public.user_roles (user_id, role_id)
    VALUES (NEW.id, (SELECT id FROM roles WHERE code = 'USER'));

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION handle_new_user();
```

**결정 근거**:
- ID 일원화 → 모든 쿼리 단순, RLS 정책 깔끔(`auth.uid() = users.id`)
- JWT의 `sub` claim이 그대로 user_id로 사용됨
- Supabase 공식 권장 패턴 (자료·예제 풍부)
- 트리거는 누락 없이 자동 동기화, Spring webhook 별도 코드 불필요
- 회원가입 시 추가 정보(real_name, birthday, gender)는 `raw_user_meta_data`로 받거나, signup 후 PATCH로 갱신

**구현 시 주의**:
- `SECURITY DEFINER` 필수 (auth 스키마 접근 권한)
- 트리거 안에서 에러 나면 회원가입 자체가 롤백됨 → 트리거 로직 단순하게 유지
- 추가 컬럼 채우는 건 Spring/Next.js의 후속 PATCH로 분리

---

### ✅ A.3 새 버전 발행 시 구매자 정책 — **무료 업그레이드**

**결정**: 한 번 구매하면 모든 후속 버전 무료 접근

**스키마 의미**:
- `idea_purchases.document_id` → 산 시점 버전 (감사·분쟁용 기록)
- 실제 접근 권한은 `idea_id` 기준
- RLS 정책: 본문 SELECT 시 `EXISTS(SELECT 1 FROM idea_purchases WHERE idea_id=... AND buyer_id=auth.uid())`

**결정 근거**:
- 마켓플레이스 사용자 친화성 (오타 수정으로 재결제 부자연스러움)
- MVP 단계에 정교한 버전별 결제 모델 불필요
- 추후 "premium 버전" 같은 개념 추가 시 호환 가능 (`idea_documents`에 `tier` 컬럼 추가 등)

**예외 처리 (정책으로 가이드)**:
- 작성자가 본문 골자를 완전히 다른 내용으로 갈아치우는 케이스 → 새 idea로 등록 권장 (가이드 문구)
- 이건 MVP 이후 운영 정책으로 보강

---

### ✅ B.4 LLM 모델 — **Claude Sonnet 4.6 (메인) + Haiku 4.5 (대화 턴)**

**결정**: 하이브리드 전략

| 사용처 | 모델 | 이유 |
|---|---|---|
| 챗봇 다중 턴 대화 | **Claude Haiku 4.5** | 입력 $0.25 / 출력 $1.25 — 저렴, 충분한 품질 |
| 챗봇 finalize (구조화 문서 생성) | **Claude Sonnet 4.6** | 입력 $3 / 출력 $15 — 정확한 스키마 준수 필요 |

**결정 근거**:
- **한국어 정서 처리** — 사용자가 일상 불편을 풀어놓는 자리, 자연스러운 대화 흐름이 핵심
- **구조화 출력 안정성** — finalize 시 정형 스키마(JSON 또는 Markdown 헤더) 안정적 준수
- **이력서 신호** — Spring AI + Claude 조합은 깔끔한 스택
- **비용 최적화** — Haiku/Sonnet 분리로 약 70% 절감

**구현 시 주의**:
- Spring AI의 추상화 레이어 사용 → 모델 변경 시 코드 영향 최소화
- API 키: `ANTHROPIC_API_KEY` 환경변수
- Resilience4j 서킷 브레이커: 5회 연속 실패 시 OPEN, 60초 후 HALF_OPEN
- 타임아웃: 30초
- 재시도: 일시적 오류(429, 503)만 3회 지수 백오프

**대안 (필요 시)**:
- 비용 더 신경 쓰면 Gemini 3 Flash 검토 (한국어 약간 불안정)
- LLM 변경 시에도 임베딩은 별도 결정이라 영향 없음

---

### ✅ B.3 임베딩 모델 — **OpenAI text-embedding-3-small**

**결정**: OpenAI text-embedding-3-small (1536차원)

**결정 근거**:
- Spring AI 통합 가장 안정적 (코드 한 줄 수준)
- 한국어 검색 품질 "충분히 양호" — Seedo 사용 시나리오에 큰 차이 없음
- 1536차원 검증된 표준, pgvector 기본 지원
- 가격 $0.02/1M — 압도적 저렴
- 추후 Cohere로 교체 시 Spring AI 추상화로 코드 변경 최소

**구현 시 주의**:
- API 키: `OPENAI_API_KEY` 환경변수 (LLM은 Anthropic이지만 임베딩은 OpenAI라 키 두 개)
- DB 컬럼: `embedding vector(1536)`
- 인덱스: `ivfflat` 또는 `hnsw` (pgvector 0.5+ 시 hnsw 권장)
- **모델 변경 = 전체 재인덱싱** 필요. 처음 결정 신중히 → 결정 완료
- 임베딩 추출 타이밍: idea PUBLISHED 시점에 비동기로

**Spring AI 설정 예시**:
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-small
```

---

## ⏳ 추후 결정 (구현하면서 정한다)

### A. 비즈니스 정책

#### A.1 Hype: 1회성 vs 토글
- 옵션 1: 1회성 — 한번 누르면 끝, 응원
- 옵션 2: 토글 — 좋아요처럼 다시 누르면 취소
- 영향: `hypes` UNIQUE 제약 + hype_count 트리거 방향
- **권장**: 토글 (사용자 후회 가능, 일반적 UX)

#### A.2 한 아이디어 → 여러 프로젝트 허용?
- MVP 권장: **허용** (여러 개발자가 같은 아이디어로 별도 프로젝트 가능)
- 보상은 첫 채택자만? 모두? — 추후 결정
- **권장**: 첫 채택자만 보상 (어뷰징 방지)

#### A.4 아이디어 가격 책정 주체
- 작성자가 정함?
- 플랫폼 고정가?
- 카테고리별 차등?
- **권장 (MVP)**: 플랫폼 고정가 (10 크레딧 등) — 단순
- **확장**: 작성자 자율 + 플랫폼 권장값 표시

#### A.5 채택 보상 금액
- 작성자 희망 금액 제시?
- 플랫폼 고정?
- **권장 (MVP)**: 플랫폼 고정 (가격의 50% 등)

#### A.6 본인 아이디어 자가 채택 가능?
- 정책: 가능
- 단 보상은 자기→자기 트랜잭션 skip
- **확정 권장**

#### A.7 아이디어 삭제
- 구매자 있는 아이디어 삭제? 보상 받은 아이디어 삭제?
- **권장**: 둘 다 hard delete 불가, 작성자도 archive만

### B. 기술 결정

#### B.5 PG (결제) 연동
- PortOne (구 아임포트) — 한국 친화
- Stripe — 글로벌
- **MVP**: 결제 없이 무료 크레딧 지급으로 시작 후 추후 연동

#### B.6 알림 채널
- 인앱만? 이메일까지? 푸시?
- **MVP**: 인앱 + Supabase Realtime 구독

#### B.7 파일 첨부
- Supabase Storage 사용 (확정)
- 버킷 분리: 프로필 / 아이디어 첨부 / 프로젝트 첨부
- 용량 제한, 형식 제한 정책 필요

#### B.8 검색 한국어 처리
- pgvector(임베딩) — 확정
- 추가로 keywords 배열 GIN 인덱스 활용 — 확정
- Postgres FTS는 한국어 사전 약함 → 일단 skip

### C. 운영 정책

#### C.1 콘텐츠 모더레이션
- 신고 시스템 도입 시점 (베타 후)
- 자동 필터링 (욕설, 스팸)
- ADMIN 1명으로 시작

#### C.2 어뷰징 방지
- 동일 IP 다중 계정?
- 자기 아이디어 자기가 사서 보상 빼먹기? → 자기 채택 시 보상 skip (A.6에서 확정 예정)
- 챗봇 호출 횟수 제한 (rate limit)

#### C.3 GDPR/PIPA
- 회원 탈퇴 시 데이터 처리 정책
- 챗봇 대화 로그 보관 기간
- 개인정보처리방침 작성 (배포 전 필수)

#### C.4 백업 / DR
- Supabase 자체 백업 정책 의존
- 결제·크레딧 트랜잭션은 별도 backup 권장

### D. 디자인 / UX

#### D.1 마크다운 에디터
- TipTap, Toast UI Editor, Lexical 중 선택
- **권장**: TipTap (커스터마이즈 좋음, React 친화)

#### D.2 챗봇 UI
- 메시지 스트리밍 (SSE) — 권장
- "충분한 정보 수집 완료" 시그널 제시 방법

#### D.3 모바일 대응
- 반응형 우선 — 확정

### E. 출시 전략

#### E.1 MVP 정의
- 챗봇 작성 + 검색 + 구매 + 채택 + 보상 + 프로젝트 피드 — 확정
- 게시판: MVP에 포함 권장 (베타테스터 모집이 핵심 기능)
- Hype/Follow: MVP 포함

#### E.2 콜드 스타트
- 초기 시드 콘텐츠 전략 필요
- 베타 기간 무료 크레딧 정책

#### E.3 메트릭
- 핵심 지표: 발행 아이디어 수, 구매 전환율, 채택률, 프로젝트 완성률
- 분석 도구: PostHog 권장 (오픈소스)
