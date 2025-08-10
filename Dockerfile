# Multi-stage Dockerfile for auth-service (Spring Boot, Gradle, Java 21)

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy Gradle wrapper and build files first for better layer caching
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x gradlew \
    && ./gradlew --no-daemon clean bootJar -x test


FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Copy built jar
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8081

# JVM tuning can be added via JAVA_OPTS
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]


