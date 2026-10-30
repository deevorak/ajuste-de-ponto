# ==========================================
# Estágio 1: Build da aplicação (JDK 25)
# ==========================================
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

COPY pom.xml mvnw ./
COPY .mvn .mvn/
RUN chmod +x ./mvnw

COPY src ./src

# Usa cache do repositório local Maven para builds rápidos e resilientes
RUN --mount=type=cache,target=/root/.m2 ./mvnw clean package -DskipTests

# ==========================================
# Estágio 2: Runtime leve (JRE 25)
# ==========================================
FROM eclipse-temurin:25-jre

WORKDIR /app

# Cria usuário não-root por segurança
RUN useradd -m -u 1001 adkuser && chown -R adkuser:adkuser /app
USER adkuser

# Copia o executável gerado
COPY --from=builder /build/target/ajuste-de-ponto-1.0.0-SNAPSHOT.jar app.jar

# Variáveis de ambiente padrão
ENV PORT=8080 \
    JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xmx512m -Xms256m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
