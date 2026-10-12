# ---- Build stage: compile the Spring Boot fat jar ---------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependency resolution separately from source changes: this layer is
# only rebuilt when pom.xml changes, not on every source edit
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ---- Runtime stage: minimal JRE, no build tools -----------------------------
FROM eclipse-temurin:21-jre

# Run as non-root instead of the container default
RUN useradd --system --uid 1001 --create-home app
USER app
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

# compose.yaml is for local dev only — never let the prod app look for a
# Docker socket that isn't there
ENV SPRING_DOCKER_COMPOSE_ENABLED=false

# Render forwards traffic to $PORT (default 10000); application.yml reads it
# via server.port=${PORT:8080} so local runs still use 8080
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "app.jar"]
