# JAR은 Jenkins 호스트(./gradlew :service-auth:bootJar)에서 빌드되어 들어옴
# 이 이미지는 빌드하지 않고 패키징만 한다 (Docker 내 Gradle 재빌드 제거)
ARG RUNTIME_IMAGE=bellsoft/liberica-runtime-container:jre-21-slim-musl

FROM ${RUNTIME_IMAGE} AS extractor
WORKDIR /workspace
COPY build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM ${RUNTIME_IMAGE}
WORKDIR /app
# 변경 빈도가 낮은 레이어부터 복사 → 의존성 레이어 캐시 재사용, push 시 app 레이어만 전송
COPY --from=extractor --chown=65532:65532 /workspace/extracted/dependencies/ ./
COPY --from=extractor --chown=65532:65532 /workspace/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=65532:65532 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=65532:65532 /workspace/extracted/application/ ./
EXPOSE 8090
USER 65532:65532
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "org.springframework.boot.loader.launch.JarLauncher"]
