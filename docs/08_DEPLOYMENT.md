# 08. 배포 (Deployment)

> **현재 셋업**: backend → Fly.io (`bom` Mumbai), web → Vercel, DB → Supabase (`ap-south-1`).
> 이 문서는 **첫 배포 가이드 + 운영 체크리스트** 다. 일상 배포는 GitHub Actions 가 자동.

---

## 아키텍처 한 그림

```text
[사용자 브라우저]
       │
       │  HTTPS
       ▼
[Vercel — seedo.vercel.app]    Next.js 정적·SSR 호스팅
       │
       │  HTTPS (springFetch + JWT)
       ▼
[Fly.io — seedo-backend.fly.dev]    Spring Boot. always-on. region bom.
       │
       │  Postgres (5432) + REST + Auth
       ▼
[Supabase — lkwwfwgieffwoeqiliqa.supabase.co]    region ap-south-1 (Mumbai).
```

같은 Mumbai region 안에서 backend ↔ DB 왕복 ~5ms. 사용자 → backend 는 ~140ms (한국 → Mumbai) — DB 트랜잭션 안정성 우선의 trade-off.

---

## 사전 준비

- Fly.io 계정 + `flyctl` 설치
  ```sh
  brew install flyctl
  flyctl auth signup    # 또는 flyctl auth login
  ```
- Vercel 계정 (Web 담당자 영역)
- Supabase 프로젝트 (이미 운영 중)

---

## 첫 배포 — Backend (Fly.io)

레포 루트에 이미 다음 파일이 있다 (#197 머지본):
- `backend/Dockerfile` — Java 25 multi-stage 빌드
- `backend/.dockerignore` — gradle 캐시·시크릿 제외
- `backend/fly.toml` — region `bom`, 1GB, always-on
- `.github/workflows/backend-deploy.yml` — main push 시 자동 deploy

### 1. 앱 생성 (한 번만)

```sh
cd backend
flyctl launch --copy-config --no-deploy
```

- `--copy-config` — 레포의 `fly.toml` 사용
- `--no-deploy` — 시크릿 등록 전이라 일단 deploy 안 함
- app 이름 충돌 시 (`seedo-backend` 점유) 다른 이름 입력

### 2. 시크릿 등록

운영 Supabase 자격증명 + OpenAI key 를 한 번에:

```sh
flyctl secrets set \
  SUPABASE_DB_URL='jdbc:postgresql://<host>:5432/postgres' \
  SUPABASE_DB_USER='postgres' \
  SUPABASE_DB_PASSWORD='...' \
  SUPABASE_JWKS_URL='https://lkwwfwgieffwoeqiliqa.supabase.co/auth/v1/.well-known/jwks.json' \
  SUPABASE_JWT_ISSUER='https://lkwwfwgieffwoeqiliqa.supabase.co/auth/v1' \
  SUPABASE_SERVICE_ROLE_KEY='...' \
  OPENAI_API_KEY='sk-proj-...' \
  PAYMENT_WEBHOOK_SECRET='임의의 긴 문자열'
```

> 값들은 로컬 `backend/.env` 의 값 그대로. **레포에 절대 커밋 X**.

### 3. 첫 배포

```sh
flyctl deploy --remote-only
```

- 빌드는 Fly 의 원격 머신에서 — 로컬 docker daemon 안 필요
- ~3~5분 (의존성 다운로드 + JVM 빌드)
- 끝나면 `seedo-backend.fly.dev` 가 응답하는지 `curl https://seedo-backend.fly.dev/swagger-ui/index.html`

### 4. CI 자동 배포 활성화

GitHub Actions 가 main push 마다 자동 deploy 하려면 `FLY_API_TOKEN` 시크릿 등록:

```sh
flyctl auth token   # 출력된 토큰 복사
```

GitHub 레포 → Settings → Secrets and variables → Actions → **New repository secret**:
- Name: `FLY_API_TOKEN`
- Value: 위 토큰

이후 `main` 에 backend 변경이 머지되면 `.github/workflows/backend-deploy.yml` 가 자동 실행 → 새 버전 deploy.

---

## 첫 배포 — Web (Vercel)

> Web 은 다른 팀원 영역이지만 참고용.

1. <https://vercel.com> 로그인 → New Project
2. GitHub 레포 `seedo-project/seedo` 선택
3. **Root Directory**: `web` (중요 — 모노레포)
4. **Framework Preset**: Next.js (자동 감지)
5. **Environment Variables**:
   ```dotenv
   NEXT_PUBLIC_SUPABASE_URL=https://lkwwfwgieffwoeqiliqa.supabase.co
   NEXT_PUBLIC_SUPABASE_ANON_KEY=eyJhbGc...
   NEXT_PUBLIC_API_BASE_URL=https://seedo-backend.fly.dev
   # NEXT_PUBLIC_DEV_SKIP_AUTH 는 운영에서 제거 또는 false
   ```
6. Deploy

이후 `main` push 시 자동 배포.

---

## Supabase 운영 설정

### Auth → URL Configuration
- **Site URL**: `https://seedo.vercel.app` (또는 자체 도메인)
- **Redirect URLs**: 위 + `https://seedo-backend.fly.dev` (Swagger 우회 인증용, 선택)

### Email Templates (선택)
- 가입 확인 / 비밀번호 재설정 메일 한국어 번역

---

## 일상 배포 흐름

```text
[개발자 PR]
   ↓ main 머지
   ├─ [backend/** 변경]  → backend-deploy.yml → flyctl deploy → Fly.io 새 버전
   └─ [web/** 변경]      → Vercel 자동 빌드 → 새 버전
```

Vercel 은 자체 GitHub 연동, GitHub Actions 불필요.

---

## 운영 체크리스트

### 배포 후 확인
```sh
# Backend health
curl https://seedo-backend.fly.dev/swagger-ui/index.html   # 200 OK 필요

# Backend 로그
flyctl logs                       # 실시간 tail
flyctl logs --since 1h            # 최근 1시간

# 메모리 / CPU
flyctl status
flyctl metrics                    # Grafana 대시보드 링크
```

### 자주 만나는 함정

| 증상 | 원인 / 해결 |
|---|---|
| `OPENAI_API_KEY` 없다고 5xx | `flyctl secrets set OPENAI_API_KEY=...` 후 자동 재시작 |
| Flyway 마이그레이션 실패 | 운영 DB 의 데이터가 신규 CHECK 와 충돌 — V21 #185 처럼 백필 SQL 먼저 |
| OOM (메모리 초과) | 1GB → 2GB scale up: `flyctl scale memory 2048` |
| 첫 요청 느림 | `auto_stop_machines = "off"` 인지 확인. JVM cold start 차단 |
| GitHub Actions 배포 fail | `FLY_API_TOKEN` 만료 확인 — `flyctl tokens create deploy` 로 deploy 전용 토큰 발급 권장 |

---

## 비용 가이드 (예상)

| 구성 | 월 비용 |
|---|---|
| Fly.io shared-cpu-1x 1GB always-on | $0~$5 |
| Vercel 무료 tier | $0 |
| Supabase 무료 tier | $0 (운영 트래픽 늘면 Pro $25) |
| **합계** | **$0~$5/월 (MVP)** |

스케일 후:
- Fly.io 2GB: ~$10/월
- Fly.io 추가 region: 인스턴스 수 만큼 곱
- Supabase Pro: $25/월 (PITR, 더 큰 DB)

---

## 다음 단계 (선택)

- [ ] 자체 도메인 (`api.seedo.app`, `seedo.app`) 연결
- [ ] Sentry 에러 모니터링
- [ ] PostHog 메트릭 (FE 사용자 행동)
- [ ] PortOne 실연동 → MVP 무료 크레딧 → 실결제 (별도 PR — A1/A2 follow-up)
- [ ] Fly.io 백업 / Supabase PITR 활성화
