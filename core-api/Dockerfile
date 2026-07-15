# The jar is arch-independent, so the Gradle stage always runs natively on the
# build host ($BUILDPLATFORM) and only the JRE stage below is per-target-arch —
# without this, arm64 CI builds would run Gradle under QEMU emulation. The ARG
# default keeps the legacy (non-BuildKit) builder working for local compose.
ARG BUILDPLATFORM=linux/amd64
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
