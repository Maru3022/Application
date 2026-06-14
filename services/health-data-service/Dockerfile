FROM eclipse-temurin:21-jre-alpine

# Upgrade ALL Alpine packages to pick up latest security patches (openssl CVE-2026-45447 etc.)
# This ensures openssl >= 3.5.7-r0 which fixes CVE-2026-45447 (Heap Use-After-Free in PKCS7_verify).
RUN apk update && apk upgrade --no-cache && apk add --no-cache tini && rm -rf /var/cache/apk/*

RUN addgroup -g 1000 healthlife && adduser -u 1000 -G healthlife -s /bin/sh -D healthlife

WORKDIR /app

COPY --chown=healthlife:healthlife target/*.jar app.jar
RUN chmod 444 app.jar

EXPOSE 8083

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+OptimizeStringConcat -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8"

USER healthlife

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8083/internal/actuator/health/liveness || exit 1
