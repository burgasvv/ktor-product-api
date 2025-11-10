
FROM postgres:latest

FROM maven:3.9.9 AS build
COPY pom.xml .
COPY /src ./src/
RUN mvn clean package -DskipTests

FROM openjdk:11 AS prod
COPY --from=build target/ktor-product-api-0.0.1.jdk ktor-product-api.jdk
EXPOSE 9000

ENTRYPOINT ["java", "-jar", "ktor-product-api.jdk"]