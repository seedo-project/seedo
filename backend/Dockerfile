# =====================================================================
# Multi-stage Dockerfile for Spring Boot 3.5 + Java 25.
# 빌드 결과는 ~250MB JRE 런타임 이미지 — Fly.io 의 256MB tier 도 빠듯하지만 운영은 1GB.
# =====================================================================

# ----- Stage 1: Build -----
# Eclipse Temurin 25 JDK — Gradle wrapper 가 toolchain 으로 강제하는 버전 일치.
# debian-slim 보다 alpine 이 작지만 musl 호환성 회피 위해 일반 이미지 (Hibernate / Netty 등 native lib).
FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

# 의존성 / Gradle 캐시 우선 — 의존성 변경 없으면 Docker layer 캐시 재사용 → 후속 빌드 빠름.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies

# 소스 + jar 빌드 (test 안 돌림 — CI 가 PR 단계에서 검증).
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ----- Stage 2: Runtime -----
FROM eclipse-temurin:25-jre AS runtime

# non-root user — privilege escalation 차단의 1차 방어.
RUN groupadd -r app && useradd -r -g app -d /app -s /sbin/nologin app

WORKDIR /app

# Spring Boot fat jar 한 개만 복사 (의존성 다 포함).
COPY --from=build --chown=app:app /workspace/build/libs/*.jar app.jar

USER app
EXPOSE 8080

# JVM 옵션:
# - MaxRAMPercentage: 컨테이너 메모리 한계의 75% 까지만 heap 으로 사용. 나머지는 metaspace / direct buffer.
# - UseG1GC: Java 25 기본이지만 명시.
# preferIPv4Stack 은 의도적으로 안 박는다 — Netty native epoll 이 IPv6 소켓을 시도하면서
# EAFNOSUPPORT(-97) 로 깨진다 (#206). Fly.io outbound 는 dual-stack 이라 default 가 안전.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]
