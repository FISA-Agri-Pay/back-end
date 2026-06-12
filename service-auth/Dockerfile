ARG BUILDER_IMAGE=gradle:8.10.2-jdk21
ARG RUNTIME_IMAGE=bellsoft/liberica-runtime-container:jre-21-slim-musl

FROM ${BUILDER_IMAGE} AS build
WORKDIR /workspace
COPY . .
RUN gradle :service-auth:bootJar --no-daemon -x test

FROM ${RUNTIME_IMAGE}
WORKDIR /app
COPY --from=build --chown=65532:65532 /workspace/service-auth/build/libs/*.jar app.jar
EXPOSE 8091
USER 65532:65532
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
