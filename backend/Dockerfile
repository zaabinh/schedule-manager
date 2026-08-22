FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
RUN mvn --batch-mode --file pom.xml dependency:go-offline
COPY src src
RUN mvn --batch-mode --file pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 app \
    && mkdir -p /var/lib/schedule-manager/uploads \
    && chown -R 10001:0 /var/lib/schedule-manager
WORKDIR /app
COPY --from=build /workspace/target/schedule-manager-backend-*.jar app.jar
USER 10001
ENV FILE_STORAGE_LOCAL_ROOT=/var/lib/schedule-manager/uploads
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-Djava.security.egd=file:/dev/urandom","-jar","/app/app.jar"]
