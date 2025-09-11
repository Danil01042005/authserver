FROM gradle:8.8.0-jdk21 AS build
WORKDIR /workspace

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.vfs.watch=false"
ENV GRADLE_USER_HOME=/workspace/.gradle

COPY build.gradle settings.gradle ./
COPY src src

RUN gradle --no-daemon bootJar -x test


FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]


