# =============================================================================
# PassTheo — passtheo-content-service
# 3-stage secure build: jlink custom JRE → distroless runtime → app copy
# =============================================================================
# Note: Build JAR locally first with `./gradlew bootJar -x test`

# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — jlink (custom minimal JRE)
# Detects required Java modules from JAR, builds stripped JRE.
# Result: ~40% smaller than full JRE, no unused modules.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS jlink

COPY build/libs/passtheo-content-service.jar /app/app.jar

RUN apk add --no-cache binutils \
    && jdeps \
        --ignore-missing-deps \
        --print-module-deps \
        --multi-release 21 \
        --recursive \
        /app/app.jar > /tmp/modules.txt \
    # Always include Spring Boot baseline modules
    && echo ",java.base,java.logging,java.naming,java.management,java.instrument,\
java.security.jgss,java.xml.crypto,jdk.crypto.cryptoki,jdk.crypto.ec,\
jdk.jfr,jdk.management,jdk.unsupported" >> /tmp/modules.txt \
    && MODULES=$(cat /tmp/modules.txt | tr -d '\n' | sed 's/,,*/,/g' | sed 's/^,//;s/,$//') \
    && jlink \
        --add-modules "$MODULES" \
        --strip-debug \
        --no-man-pages \
        --no-header-files \
        --compress=2 \
        --output /custom-jre


# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — runtime (production image)
# Google Distroless Java base — includes JRE
# ─────────────────────────────────────────────────────────────────────────────
FROM gcr.io/distroless/java21-debian12 AS runtime

# Copy application JAR
WORKDIR /app
COPY build/libs/passtheo-content-service.jar ./app.jar

# OCI image labels (populated at build time via --build-arg)
ARG BUILD_DATE
ARG GIT_COMMIT
ARG SERVICE_NAME=passtheo-content-service
ARG SERVICE_VERSION=unknown

LABEL org.opencontainers.image.title="${SERVICE_NAME}" \
      org.opencontainers.image.version="${SERVICE_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.vendor="PassTheo" \
      org.opencontainers.image.base.name="gcr.io/distroless/cc-debian12"

# JVM tuning — container-aware, no heap guessing
ENV JAVA_TOOL_OPTIONS="\
-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:InitialRAMPercentage=50.0 \
-XX:+UseG1GC \
-XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom \
-Dfile.encoding=UTF-8 \
-Duser.timezone=UTC"

# Only expose app port — management port never exposed externally
EXPOSE 8087

# Docker-level health check (separate from K8s probes)
# Note: Distroless doesn't include curl/wget, so we skip HEALTHCHECK in Dockerfile
# Health checks are handled by docker-compose healthcheck configuration

ENTRYPOINT ["java", "-jar", "app.jar"]
