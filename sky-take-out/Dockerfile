FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY sky-common/pom.xml sky-common/pom.xml
COPY sky-pojo/pom.xml sky-pojo/pom.xml
COPY sky-server/pom.xml sky-server/pom.xml
RUN mvn -B -ntp -pl sky-server -am dependency:go-offline

COPY sky-common/src sky-common/src
COPY sky-pojo/src sky-pojo/src
COPY sky-server/src sky-server/src
RUN mvn -B -ntp -pl sky-server -am -DskipTests package
RUN set -eu; \
    jar_count="$(find /workspace/sky-server/target -maxdepth 1 -type f -name 'sky-server-*.jar' ! -name '*.original' | wc -l)"; \
    test "$jar_count" -eq 1 || { echo "Expected exactly one executable sky-server jar, found $jar_count" >&2; exit 1; }; \
    jar_path="$(find /workspace/sky-server/target -maxdepth 1 -type f -name 'sky-server-*.jar' ! -name '*.original')"; \
    cp "$jar_path" /workspace/app.jar

FROM eclipse-temurin:17.0.13_11-jre-alpine
ARG VCS_REF=unknown
LABEL org.opencontainers.image.source="https://github.com/myit521/mobile-retail-mall" \
      org.opencontainers.image.revision="${VCS_REF}"

RUN addgroup -S sky && adduser -S -G sky -u 10001 sky
WORKDIR /app
COPY --from=builder --chown=sky:sky /workspace/app.jar /app/app.jar
USER sky
EXPOSE 8080 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=12 \
  CMD wget --quiet --output-document=- http://localhost:8081/actuator/health/readiness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
