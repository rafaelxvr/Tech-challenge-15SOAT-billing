# Imagens base fixadas por digest imutável (controle S-10); a versão legível fica só no comentário.
# maven:3.9-eclipse-temurin-17-alpine
FROM maven@sha256:b7c8d7298dbabbe750441736cd99a1d120a58083d5af8d2bd75a1624cfdd8f3a AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# eclipse-temurin:17-jre-alpine
FROM eclipse-temurin@sha256:c4a33e6ddb738fe4be0a28a613ea0940cec42d25d7263c3e2238c43354cb19b0 AS runtime
# CVE-2026-111497 (HIGH): brotli-libs 1.2.0-r1 da base; corrigido na 1.2.1-r0. Remover quando o digest da base já trouxer a correção.
RUN apk upgrade --no-cache brotli-libs \
    && addgroup -S -g 10001 oficina && adduser -S -D -u 10001 -G oficina oficina
WORKDIR /app
COPY --from=builder --chown=10001:10001 /app/target/billing-service-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
# Nenhum perfil de pagamento fake por padrão (S-5): o perfil e as credenciais vêm do overlay do cluster.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
