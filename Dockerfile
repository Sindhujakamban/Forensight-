# Zero-cost cloud backend: Spring Boot only.
# TruFor inference runs on the investigator's own computer in the free setup.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
ENV PORT=8080
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m"
EXPOSE 8080
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
