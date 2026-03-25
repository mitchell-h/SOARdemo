FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY logs-api ./logs-api
COPY core-banking-service ./core-banking-service
COPY admin-ui ./admin-ui
COPY fraud-detection-engine ./fraud-detection-engine
COPY verification-service ./verification-service
COPY data-generation-service ./data-generation-service
COPY workflows ./workflows
RUN chmod +x gradlew && ./gradlew :admin-ui:shadowJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/admin-ui/build/libs/*-all.jar app.jar
EXPOSE 7003
CMD ["java", "-jar", "app.jar"]
