# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

# Maven is not bundled in eclipse-temurin; install from Alpine package index
RUN apk add --no-cache maven

WORKDIR /app

# Copy pom.xml first so dependency download is a cached Docker layer.
# This layer is only invalidated when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Use JRE-only image — JDK, Maven, and source code are discarded
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Health check against the Spring Boot Actuator health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --spider -q http://localhost:8080/actuator/health || exit 1

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
