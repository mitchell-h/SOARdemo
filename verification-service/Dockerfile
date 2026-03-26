FROM eclipse-temurin:17-jdk AS builder
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
COPY case-management-service ./case-management-service
RUN chmod +x gradlew && ./gradlew :verification-service:shadowJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/verification-service/build/libs/*-all.jar app.jar
EXPOSE 7005
CMD ["java", "-jar", "app.jar"]
