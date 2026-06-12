FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle :service-core:bootJar --no-daemon -x test

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build --chown=65532:65532 /workspace/service-core/build/libs/*.jar app.jar
EXPOSE 8090
USER 65532:65532
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
