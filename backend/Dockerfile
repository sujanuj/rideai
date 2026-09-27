# ---- build stage: compile and package the jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first so Docker can cache this layer
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- run stage: a small JRE image with just the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 spring
USER spring
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
