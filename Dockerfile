# Imagens base fixadas por digest imutável (controle S-10); a versão legível fica só no comentário.
# maven:3.9-eclipse-temurin-17-alpine
FROM maven@sha256:b7c8d7298dbabbe750441736cd99a1d120a58083d5af8d2bd75a1624cfdd8f3a AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Agente New Relic: artefato imutável com checksum verificado (mesma versão do os-service).
FROM maven@sha256:880934ae394bf91bc3e57d573e4fc04774f064f3c4df7ccd7cc10b3b126737bf AS newrelic-agent
ARG NEW_RELIC_JAVA_AGENT_VERSION=9.4.0
ARG NEW_RELIC_JAVA_AGENT_SHA256=1f8f42d25e6565a1a7088543deeeefacdd2b028761ac94743dd1715cf7ddf5f4
RUN mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy       -Dartifact=com.newrelic.agent.java:newrelic-agent:${NEW_RELIC_JAVA_AGENT_VERSION}:jar       -DoutputDirectory=/opt/newrelic &&     mv /opt/newrelic/newrelic-agent-${NEW_RELIC_JAVA_AGENT_VERSION}.jar /opt/newrelic/newrelic.jar &&     echo "${NEW_RELIC_JAVA_AGENT_SHA256}  /opt/newrelic/newrelic.jar" | sha256sum -c -

# eclipse-temurin:17-jre-alpine
FROM eclipse-temurin@sha256:c4a33e6ddb738fe4be0a28a613ea0940cec42d25d7263c3e2238c43354cb19b0 AS runtime
# CVEs HIGH do Alpine (brotli-libs, openssl, libexpat) presentes no digest da base; apk upgrade completo traz as correções. Revisar quando o digest da base já as incluir.
RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 oficina && adduser -S -D -u 10001 -G oficina oficina
WORKDIR /app
COPY --from=newrelic-agent --chown=10001:10001 /opt/newrelic/newrelic.jar /app/newrelic/newrelic.jar
COPY --chown=10001:10001 docker/entrypoint.sh /app/entrypoint.sh
COPY --from=builder --chown=10001:10001 /app/target/billing-service-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
# Nenhum perfil de pagamento fake por padrão (S-5): o perfil e as credenciais vêm do overlay do cluster.
# Observabilidade desligada por padrão; o overlay liga com OBSERVABILITY_ENABLED=true e injeta
# NEW_RELIC_LICENSE_KEY por Secret (a imagem nunca contém a chave).
ENV NEW_RELIC_APP_NAME=oficina-billing-service
ENV OBSERVABILITY_ENABLED=false
ENTRYPOINT ["sh", "/app/entrypoint.sh"]
