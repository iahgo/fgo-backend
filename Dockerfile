# =================================================================
# Dockerfile — MS Operação FGO (build JVM, não native)
# =================================================================
#
# Build JVM é mais rápido para compilar (30s vs 10min para native)
# e suficiente para um servidor caseiro.
#
# Para build local:
#   mvn package -DskipTests
#   docker build -t ms-operacao:local .
#
# Para rodar local:
#   docker run -p 8080:8080 \
#     -e DB2_HOST=<tailscale-ip-centos> \
#     -e DB2_PORT=50000 \
#     -e DB2_DATABASE=FGODB \
#     -e DB2_USER=db2inst1 \
#     -e DB2_PASSWORD=senha \
#     -e REDIS_PASSWORD=senha \
#     -e QUARKUS_PROFILE=prod \
#     ms-operacao:local
# =================================================================

# -----------------------------------------------------------------
# ESTÁGIO 1: Build com Maven
# O GitHub Actions usa este estágio para compilar o JAR.
# -----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copia o pom.xml primeiro para aproveitar o cache de layers do Docker
# (as dependências só são baixadas novamente se o pom.xml mudar)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copia o código fonte e compila
COPY src ./src
RUN mvn package -DskipTests -q

# -----------------------------------------------------------------
# ESTÁGIO 2: Runtime JVM
# Imagem final menor — apenas o JRE, sem Maven ou sources.
# -----------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copia apenas o JAR compilado do estágio de build
# O Quarkus gera a estrutura em target/quarkus-app/
COPY --from=build /app/target/quarkus-app/lib/           /app/lib/
COPY --from=build /app/target/quarkus-app/*.jar          /app/
COPY --from=build /app/target/quarkus-app/app/           /app/app/
COPY --from=build /app/target/quarkus-app/quarkus/       /app/quarkus/

# Porta padrão do Quarkus
EXPOSE 8080

# Health check: o Docker verifica se o container está saudável
# a cada 30s. Usa o endpoint /q/health/ready do Quarkus.
# Enquanto o StartupEvent estiver rodando (carregando Redis),
# o /q/health/ready retorna 503 — Docker não envia tráfego para o container.
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
  CMD wget -qO- http://localhost:8080/q/health/ready || exit 1

# Usuário não-root por segurança
RUN addgroup -S fgo && adduser -S fgo -G fgo
USER fgo

# Ativa o perfil prod (application-prod.properties)
ENV QUARKUS_PROFILE=prod

CMD ["java", "-jar", "/app/quarkus-run.jar"]
