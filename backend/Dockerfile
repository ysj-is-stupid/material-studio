FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./
COPY maven-settings.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -s maven-settings.xml verify

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/material-studio-backend-0.1.0.jar app.jar
RUN mkdir -p /data/uploads /data/results
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
