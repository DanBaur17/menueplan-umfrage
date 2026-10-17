# Build
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/quarkus-app/ ./
ENV JAVA_OPTS_APPEND="-XX:MaxRAMPercentage=75"
CMD ["sh", "-c", "java $JAVA_OPTS_APPEND -jar quarkus-run.jar"]
