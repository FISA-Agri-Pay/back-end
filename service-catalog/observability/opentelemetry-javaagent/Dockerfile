FROM busybox:1.36

RUN mkdir -p /otel \
    && chown -R 10001:10001 /otel

COPY --chown=10001:10001 opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

USER 10001:10001