# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

COPY shared/ shared/
COPY pom.xml ./

RUN mvn install -pl shared/common-dto,shared/common-exceptions,shared/common-security     -DskipTests -B -q

COPY services/mental-service/ services/mental-service/
RUN mvn package -pl services/mental-service -DskipTests -B -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache tini

RUN addgroup -g 1000 healthlife && adduser -u 1000 -G healthlife -s /bin/sh -D healthlife

WORKDIR /app

COPY --from=builder --chown=healthlife:healthlife /workspace/services/mental-service/target/*.jar app.jar
RUN chmod 444 app.jar

EXPOSE 8084

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+OptimizeStringConcat -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8"

USER healthlife

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8084/internal/actuator/health/liveness || exit 1
