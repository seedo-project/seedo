# IntelliJ IDEA — Seedo 백엔드 셋업 가이드

VS Code 에서 작업하던 사람이 IntelliJ 로 옮길 때 30 분 안에 동작 확인까지 끝낼 수 있도록 정리한 셋업 절차입니다. 백엔드 (`backend/`, Spring Boot + Java 25) 만 다룹니다 — Next.js (`web/`) 는 IntelliJ 가 최신 Next 16 + Tailwind v4 + shadcn base-nova 인식이 약하므로 VS Code 유지를 권장합니다.

---

## 0. 사전 준비

- **IntelliJ IDEA Ultimate** 권장. Community 에디션도 가능하지만 Spring Boot 디버깅 / Database 도구 / HTTP Client / Swagger 미리보기는 Ultimate 만 제공됩니다.
- **JDK 25** 시스템에 설치. Gradle toolchain 이 자동으로 받아주긴 하지만 IntelliJ 의 Project SDK 도 25 로 잡아둬야 인덱서가 정확히 작동합니다.
- Docker Desktop 또는 Colima — Testcontainers 통합 테스트 실행 시 필요합니다.

---

## 1. 프로젝트 열기

모노레포라 두 가지 방식이 있고 권장은 첫 번째입니다.

### (권장) `backend/` 만 열기

`File → Open` 으로 `seedo/backend` 폴더를 선택합니다. IntelliJ 가 `build.gradle` 을 자동 인식해서 Gradle 프로젝트로 import 합니다.

### 루트 (`seedo/`) 열기 (비권장)

루트를 열면 `web/` Next.js 도 같이 들어와서 IntelliJ 가 헷갈리고, Gradle 인식이 `backend/build.gradle` 까지 한 단계 더 깊어집니다. 둘 다 손대야 하는 경우엔 IntelliJ 로 `backend/` 만, VS Code 로 `web/` 만 따로 여는 게 깔끔합니다.

### 기존 `.idea/` 가 있다면

이전에 한 번 열어둔 흔적이 있으면 그 폴더가 IDE 설정으로 남아있습니다. 헷갈리는 동작이 보이면 `.idea/` 통째로 삭제 후 다시 Open 하면 깨끗하게 재생성됩니다.

---

## 2. Gradle 동기화

처음 열면 우측 하단에 **"Load Gradle Project"** 알림이 뜹니다. 클릭 → 자동으로 의존성 다운로드 + 인덱싱.

`Settings → Build → Build Tools → Gradle` 에서 다음을 확인합니다:

| 항목 | 값 |
|---|---|
| Gradle JVM | `Project SDK 25` (또는 toolchain 이 자동으로 잡아둔 25) |
| Build and run using | `Gradle` 또는 `IntelliJ IDEA`. IntelliJ 가 더 빠르고 Gradle 이 toolchain 호환성에서 안전. 둘 다 가능 |
| Run tests using | `Gradle` 권장 — Testcontainers 와 가장 잘 맞음 |

---

## 3. Application 실행 구성

`SeedoApplication.java` 를 열고 `public static void main` 옆 ▶ 아이콘을 클릭하면 자동으로 Run Configuration 이 생성됩니다. 거기서 **환경 변수** 만 추가하면 됩니다.

`Run → Edit Configurations → SeedoApplication → Environment variables` 에서 다음을 입력합니다.

```
SUPABASE_DB_URL=jdbc:postgresql://aws-...pooler.supabase.com:6543/postgres
SUPABASE_DB_USER=postgres.<project-ref>
SUPABASE_DB_PASSWORD=<실제 비번>
SUPABASE_JWKS_URL=https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
SUPABASE_JWT_ISSUER=https://<project-ref>.supabase.co/auth/v1
SUPABASE_SERVICE_ROLE_KEY=<service_role 키>
OPENAI_API_KEY=<선택. 비어두면 임베딩만 실패하고 다른 흐름은 정상>
```

> **KR ISP 접속 주의**: KR ISP 에서 Supabase Direct (IPv6) 연결이 닿지 않아 **Session pooler (포트 6543)** 사용이 필수입니다. URL 위 형식 그대로.

저장 후 ▶ Run 또는 🐞 Debug 로 실행합니다. 콘솔에 `Started SeedoApplication ... on port(s): 8080` 이 뜨면 성공입니다.

---

## 4. 동작 확인

서버를 띄운 뒤 브라우저로:

- `http://localhost:8080/swagger-ui/index.html` — Swagger UI 에서 모든 API 목록 + 직접 호출 테스트
- `http://localhost:8080/v3/api-docs` — raw OpenAPI JSON (Postman / Insomnia 등에 import 가능)

인증된 API 호출은 Swagger UI 우측 상단 **Authorize** 버튼에서 Supabase 발급 JWT 를 입력하면 모든 요청에 `Authorization: Bearer <token>` 헤더가 자동 부착됩니다.

---

## 5. Database 연결 (선택, Ultimate)

Flyway 마이그레이션 결과를 IDE 안에서 바로 쿼리하고 싶다면.

`View → Tool Windows → Database` → 좌상단 `+` → `PostgreSQL` 선택:

| 항목 | 값 |
|---|---|
| Host / Port / DB | Supabase pooler 정보 (6543) |
| User / Password | 위 환경 변수와 동일 |

`Test Connection` 통과하면 `Schemas` 펼쳐서 `public.users`, `idea_*`, `project_*`, `rewards` 등이 보입니다. 새 마이그레이션 적용 후 컬럼/CHECK 확인할 때 편합니다.

---

## 6. 추천 플러그인

대부분 IntelliJ Ultimate 에 내장돼 있어 별도 설치 불필요:

- **Spring Boot Tools** (내장) — `application.yml` 자동완성, Bean 그래프
- **Database Tools and SQL** (Ultimate 내장)
- **Gradle** (내장)

추가로 깔면 좋은 것:

- **GitToolBox** — 라인별 git blame 인라인 표시
- **Rainbow Brackets** — 중첩 괄호 색 구분 (긴 trasaction 메서드 가독성 ↑)
- **Key Promoter X** — 마우스로 클릭한 메뉴의 단축키를 팝업으로 알려줌 (단축키 익히기용)

설치하지 않아도 되는 것:

- **Lombok** — Seedo 프로젝트는 Lombok 을 사용하지 않습니다. record + 생성자 직접 작성이 기본.

---

## 7. 자주 쓰는 단축키 (macOS)

| 단축키 | 동작 |
|---|---|
| `⌘ + O` | 클래스명으로 파일 열기 |
| `⌘ + ⇧ + O` | 파일명으로 파일 열기 |
| `⌘ + ⌥ + O` | 심볼 (메서드/필드) 로 열기 |
| `⌘ + B` | 정의로 이동 / Find Usages |
| `⌘ + N` (편집기 안) | Generate (constructor, getter 등) |
| `⌃ + R` | 현재 Run Configuration 실행 |
| `⌃ + ⇧ + R` | 현재 커서 위치의 테스트만 실행 |
| `⌘ + ⇧ + F` | 전체 검색 |
| `⇧ ⇧` (Shift 두 번) | Search Everywhere — 가장 자주 씀 |

> Windows / Linux 는 위 단축키의 `⌘` 을 `Ctrl`, `⌥` 을 `Alt`, `⇧` 을 `Shift` 로 치환.

---

## 8. 주의할 함정

다음 함정은 매번 IntelliJ 로 처음 옮길 때 한 번씩 부딪힙니다:

- **`.idea/` 디렉토리는 `.gitignore` 에 추가하는 게 좋습니다**. IDE 설정은 사람마다 달라서 공유하면 노이즈만 됩니다. 이미 staged 된 적이 있다면 별도 chore PR 로 `git rm --cached .idea/...` + `.gitignore` 갱신.
- **Gradle JVM 이 21 처럼 낮게 잡혀있으면 컴파일 실패**. `Settings → Build → Build Tools → Gradle → Gradle JVM` 에서 Project SDK 25 로 강제.
- **`bootRun` 시 환경 변수 누락**: `application.yml` 의 `${SUPABASE_DB_URL}` 같은 placeholder 가 비어 부팅 실패합니다. Run Configuration 의 Environment variables 다시 확인.
- **테스트 실행 시 Docker 미실행**: Testcontainers 가 Docker Desktop / Colima 를 요구합니다. Docker 안 켜져있으면 통합 테스트가 컨테이너 부팅 단계에서 실패. 로컬에선 Testcontainers IT 안 돌리는 게 컨벤션이라 자주 마주칠 일은 적지만, 한 번 돌리고 싶으면 Docker 부터 켜야 합니다.
- **포트 8080 충돌**: VS Code 또는 터미널에서 띄워놓은 `bootRun` 이 살아있으면 IntelliJ 실행이 "Port 8080 already in use" 로 실패합니다. `lsof -i :8080` 로 PID 확인 후 죽이거나, 둘 중 하나는 다른 포트로 띄웁니다.

---

## 참고

- 백엔드 일상 작업 가이드: `backend/CLAUDE.md`
- 자주 쓰는 명령: 동 문서 §"자주 쓰는 명령"
- API 문서 진입: 동 문서 §"API 문서 (Swagger / OpenAPI)"
