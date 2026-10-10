# Build and container execution are provided but were not run in the authoring environment.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY . .
RUN bash scripts/build.sh
FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 hub && useradd --uid 10001 --gid hub --no-create-home hub && mkdir -p /app/var /app/drivers /app/plugins && chown -R hub:hub /app
WORKDIR /app
COPY --from=build --chown=hub:hub /src/build/connector-hub.jar /app/connector-hub.jar
USER 10001:10001
ENV HUB_BIND=0.0.0.0 HUB_PORT=8080 HUB_DATA_DIR=/app/var
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD ["java", "-cp", "/app/connector-hub.jar", "io.ahad.connectorhub.server.HealthCheck"]
ENTRYPOINT ["java", "-cp", "/app/connector-hub.jar:/app/drivers/*:/app/plugins/*", "io.ahad.connectorhub.server.Main"]
