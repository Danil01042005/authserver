# Multi-stage Dockerfile for api-gateway (Spring Boot, Gradle, Java 21)

FROM gradle:8.8.0-jdk21 AS build
WORKDIR /workspace

# Copy build files first for better layer caching
COPY build.gradle settings.gradle ./
COPY src src

RUN gradle --no-daemon clean bootJar -x test


FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Install curl for healthchecks
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Copy built jar
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080

# JVM tuning can be added via JAVA_OPTS
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]


