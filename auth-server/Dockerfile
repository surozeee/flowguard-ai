FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S flowguard && adduser -S flowguard -G flowguard
USER flowguard
COPY app.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
