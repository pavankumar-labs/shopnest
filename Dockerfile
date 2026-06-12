
FROM maven:3.9.6-eclipse-temurin-17 AS jarbuilder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=jarbuilder /app/target/*.jar  app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]