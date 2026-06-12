FROM eclipse-temurin:21-jdk-alpine AS jre-builder
RUN jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.xml,jdk.crypto.ec,jdk.jfr,jdk.localedata,jdk.net,jdk.unsupported \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6 \
    --output /custom-jre

FROM eclipse-temurin:21-jdk-alpine AS extractor
WORKDIR /workspace
COPY build/libs/*.jar build/libs/
RUN set -eux; \
    jar_file="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | sort | head -n 1)"; \
    test -n "$jar_file"; \
    cp "$jar_file" app.jar; \
    java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM alpine:3.23
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
RUN apk add --no-cache ca-certificates tzdata \
    && addgroup -S appgroup \
    && adduser -S appuser -G appgroup
COPY --from=jre-builder /custom-jre ${JAVA_HOME}
WORKDIR /app
COPY --from=extractor --chown=appuser:appgroup /workspace/extracted/dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /workspace/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=appuser:appgroup /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /workspace/extracted/application/ ./
EXPOSE 8081
USER appuser
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "org.springframework.boot.loader.launch.JarLauncher"]
