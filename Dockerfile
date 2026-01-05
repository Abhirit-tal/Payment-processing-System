# Multi-stage Dockerfile: build with Maven, run with a lightweight JRE image

FROM maven:3.8.8-jdk-17 AS build
WORKDIR /workspace

# Copy only dependency descriptors first for better caching
COPY pom.xml ./
# Copy source
COPY src ./src

# Package the application (skip tests for faster builds; change if you want tests run in CI)
RUN mvn -B -DskipTests package

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy jar from build stage
COPY --from=build /workspace/target/payment-processing-system-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

