# Multi-stage Dockerfile: build with Maven, run with a lightweight JRE image

FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy only dependency descriptors first for better caching
COPY pom.xml ./
# Copy source
COPY src ./src

# Package the application (skip tests for faster builds; change if you want tests run in CI)
# Ensure spring-boot repackage to create executable jar
RUN mvn -B -DskipTests package spring-boot:repackage

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Install curl so docker-compose healthcheck can use it
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Copy jar from build stage
COPY --from=build /workspace/target/payment-processing-system-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
