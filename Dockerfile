# Многоступенчатая сборка mental-service (микросервис поддержки и ментального здоровья)
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY shared ./shared
COPY services ./services
RUN mvn -pl services/mental-service -am dependency:go-offline -B
RUN mvn -pl services/mental-service -am clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -g 1000 healthlife && adduser -u 1000 -G healthlife -s /bin/sh -D healthlife
COPY --from=builder /app/services/mental-service/target/*.jar app.jar
RUN chown healthlife:healthlife app.jar
USER healthlife
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
