# Seedo — Design Docs

Antigravity / Cursor / Windsurf 같은 AI IDE에서 이 폴더를 그대로 컨텍스트로 넣으면 됩니다.

## 문서 구성

| 파일 | 용도 | 우선순위 |
|---|---|---|
| `00_PROJECT_CONTEXT.md` | 전체 개요, 가장 먼저 읽힐 문서 | ★★★ |
| `01_DB_SCHEMA.md` | 모든 테이블 컬럼·제약·인덱스 | ★★★ |
| `02_BUSINESS_LOGIC.md` | 트랜잭션 시퀀스 (의사 SQL) | ★★ |
| `03_RESPONSIBILITY_SPLIT.md` | Spring vs Supabase API 분담 (설계 원칙) | ★★ |
| `04_OPEN_QUESTIONS.md` | 아직 안 정한 것들 | ★ |
| `05_ERD.md` | mermaid ERD 소스 | 참고 |
| `06_KICKOFF_DECK.md` | 팀 합의 미팅용 슬라이드 자료 | ★★ |
| `07_IMPLEMENTATION_STATUS.md` | **현재 머지된 것** — 마이그레이션 / 컨트롤러 / 어댑터 / PR 인덱스 / 미구현 항목 (03 의 실제 매핑) | ★★★ |
| `adr/` | Architecture Decision Records (구조/패턴/스택 결정 배경) | 참고 |
| `code-review-lessons.md` | CodeRabbit / 동료 리뷰에서 배운 패턴 누적 | 참고 |
| `intellij-setup.md` | 백엔드 IntelliJ IDEA 셋업 가이드 (VS Code → IntelliJ 옮길 때) | 참고 |

## Antigravity에서 사용하는 법

### 옵션 A: 프로젝트 루트에 `docs/` 디렉토리로 배치
```
seedo/
├── docs/                    ← 이 폴더 통째로
│   ├── 00_PROJECT_CONTEXT.md
│   ├── 01_DB_SCHEMA.md
│   └── ...
├── backend/                 ← Spring Boot
├── web/                     ← Next.js
└── ...
```
AI에게 "docs/ 폴더 다 읽고 작업해줘" 한 줄로 컨텍스트 잡힘.

### 옵션 B: `.cursorrules` 또는 `AGENTS.md` 같은 메타 파일로 가리키기
```
프로젝트 시작 시 docs/00_PROJECT_CONTEXT.md를 먼저 읽고,
DB 작업 시 docs/01_DB_SCHEMA.md, 비즈니스 로직 작업 시 docs/02_BUSINESS_LOGIC.md를 참조하세요.
```

### 첫 작업 추천 프롬프트
> "이 폴더의 모든 docs를 읽고, Spring Boot 프로젝트 초기 구조와 Flyway V1 마이그레이션 SQL을 만들어줘. 01_DB_SCHEMA.md의 RBAC + 크레딧 테이블만 먼저, 나머진 V2 이후로."

## 다음 단계 체크리스트

이 문서를 옮긴 다음 Antigravity에서:

- [ ] Spring Boot 프로젝트 부트스트랩 (Gradle Groovy DSL, **Java 25**, Spring Boot 3.5+)
- [ ] 의존성: spring-boot-starter-web, security, data-jpa, validation / flyway-core + flyway-database-postgresql / oauth2-resource-server / postgresql
- [ ] Supabase 프로젝트 생성, vector 확장 활성화
- [ ] Flyway V1 마이그레이션 — RBAC + users + user_credits + credit_transactions
- [ ] Supabase JWT 검증 필터 (`oauth2-resource-server` JwtDecoder + JWKS)
- [ ] 다음 Flyway V2 — ideas, idea_documents, idea_chat_*, idea_purchases
- [ ] Next.js 프로젝트 부트스트랩, supabase-js 연동
