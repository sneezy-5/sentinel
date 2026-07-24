# Runtime image for central-server. Built from the repo root build context (needs core/
# too, since central-server depends on it):
#   docker build -f central-server/Dockerfile -t sentinel-central .

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml mvnw mvnw.cmd /workspace/
COPY .mvn /workspace/.mvn
COPY core /workspace/core
COPY central-server /workspace/central-server
RUN chmod +x mvnw && ./mvnw -q -pl central-server -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN useradd --system --create-home --shell /usr/sbin/nologin sentinel
USER sentinel
WORKDIR /app
COPY --from=build /workspace/central-server/target/central-server-*.jar /app/central-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/central-server.jar"]
