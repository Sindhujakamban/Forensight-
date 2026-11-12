# Build the Spring Boot backend with Java 25.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn -B -DskipTests package

# Keep the runtime image small.
FROM eclipse-temurin:25-jre
WORKDIR /app
ENV PORT=8080
EXPOSE 8080
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
