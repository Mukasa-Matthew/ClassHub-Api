FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system classhub && useradd --system --gid classhub classhub
COPY --from=build /workspace/target/classhub-api-0.0.1-SNAPSHOT.jar app.jar
RUN chown classhub:classhub app.jar

USER classhub
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
