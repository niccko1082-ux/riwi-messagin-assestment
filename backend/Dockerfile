# Multi-módulo Maven (domain -> application -> infrastructure -> api): las capas de poms se
# copian antes que el código para cachear dependency:go-offline entre builds.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY riwi-domain/pom.xml riwi-domain/pom.xml
COPY riwi-application/pom.xml riwi-application/pom.xml
COPY riwi-infrastructure/pom.xml riwi-infrastructure/pom.xml
COPY riwi-api/pom.xml riwi-api/pom.xml
RUN mvn -q -B dependency:go-offline

COPY riwi-domain/src riwi-domain/src
COPY riwi-application/src riwi-application/src
COPY riwi-infrastructure/src riwi-infrastructure/src
COPY riwi-api/src riwi-api/src
RUN mvn -q -B -pl riwi-api -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/riwi-api/target/riwi-messaging-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
