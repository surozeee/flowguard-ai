FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S flowguard && adduser -S flowguard -G flowguard
USER flowguard
COPY app.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
