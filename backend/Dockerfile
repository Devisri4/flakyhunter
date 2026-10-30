# --- Build stage: compile the app with the Maven wrapper ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

# --- Run stage: only the JRE and the jar, running as a normal (non-root) user ---
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 flaky
COPY --from=build /app/app.jar app.jar
USER flaky
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
