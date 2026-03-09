# ════════════════════════════════════════════════════════════════
# Multi-stage build — keeps final image lean
#
# Stage 1 (builder): compiles the JAR using Maven
# Stage 2 (runtime): only the JRE + the built JAR
#
# Final image is ~200MB vs ~600MB for a single-stage build with Maven.
# ════════════════════════════════════════════════════════════════

# ── Stage 1: Build ───────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy POM first — Docker layer cache: dependencies are only
# re-downloaded when pom.xml changes, not on every source change.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Non-root user for security — never run apps as root in containers
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy only the fat JAR from the builder stage
COPY --from=builder /build/target/*.jar app.jar

# Spring Boot port
EXPOSE 8080

# JAVA_OPTS is set via docker-compose environment for tuning
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]