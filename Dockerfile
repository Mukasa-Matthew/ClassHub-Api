FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B -DskipTests package \
    && rm -rf /root/.m2/repository

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system classhub \
    && useradd --system --gid classhub --home-dir /app --shell /usr/sbin/nologin classhub \
    && mkdir -p /data/classhub-storage \
    && chown -R classhub:classhub /data/classhub-storage

COPY --from=build /workspace/target/classhub-api-0.0.1-SNAPSHOT.jar app.jar
RUN chown classhub:classhub app.jar

ENV CLASSHUB_STORAGE_PATH=/data/classhub-storage

USER classhub
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
