FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Это сработает, только если в docker-compose context указан как корень проекта (.)
COPY . .

RUN mvn clean package -pl api-gateway -am -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/api-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]