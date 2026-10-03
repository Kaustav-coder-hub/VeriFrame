FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline -B || true

COPY src ./src

EXPOSE 8080

ENTRYPOINT ["./mvnw", "spring-boot:run"]