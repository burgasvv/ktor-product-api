
FROM postgres:latest

FROM maven:3.9.9 AS build
COPY pom.xml .
COPY /src ./src/
RUN mvn clean package -DskipTests

FROM openjdk:11 AS prod
COPY --from=build target/ktor-product-api-0.0.1.jar ktor-product-api.jar
EXPOSE 9000

ENTRYPOINT ["java", "-jar", "ktor-product-api.jar"]