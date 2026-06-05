# syntax=docker/dockerfile:1
#
# Backend Spring Boot image for Railway (and any container host).
# The frontend-maven-plugin compiles Tailwind CSS during `generate-resources`,
# so `mvn package` produces a self-contained jar — no separate `npm` step needed.

##### Build stage #####
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy everything (build context trimmed by .dockerignore) and build the executable jar.
# Use the image's `mvn` (Maven 3.9) rather than ./mvnw: the official maven image sets
# MAVEN_CONFIG=/root/.m2, which the wrapper would mis-pass as a goal.
COPY . .
RUN mvn -B -ntp clean package -DskipTests

##### Runtime stage #####
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as an unprivileged user.
RUN addgroup -S app && adduser -S app -G app
USER app

# Spring Boot repackage emits tuganire-<version>.jar (the *.original is left behind).
COPY --from=build /workspace/target/tuganire-*.jar app.jar

# prod profile + container-aware heap sizing (mirrors the pom's Jib jvmFlags).
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
