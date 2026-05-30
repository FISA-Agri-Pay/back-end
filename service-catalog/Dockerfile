FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --chown=appuser:appgroup build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8081
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]