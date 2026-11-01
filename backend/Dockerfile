# Imagen del backend: compila con Maven y corre solo con el JRE.
#
# Las pruebas NO se ejecutan aqui: usan Testcontainers y necesitan un demonio
# Docker. Correrlas dentro del build exigiria Docker en Docker; el pipeline de
# CI las corre antes de construir la imagen, que es donde corresponde.

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Primero las dependencias: si el pom no cambia, esta capa se reutiliza
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre AS runtime

# curl lo usa el health check de Docker Compose contra /actuator/health
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Usuario sin privilegios
RUN groupadd --system siga && useradd --system --gid siga siga
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar
RUN chown -R siga:siga /app
USER siga

EXPOSE 8080

# La configuracion viaja como variables de entorno (docker-compose.yml):
# SPRING_DATASOURCE_URL, DB_USERNAME, DB_PASSWORD y las dos del resource server.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
