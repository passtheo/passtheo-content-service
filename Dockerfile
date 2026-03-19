FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src
COPY config config

RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
COPY --from=build /app/build/libs/passtheo-content-service.jar app.jar

RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8087

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -qO- http://localhost:8087/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
