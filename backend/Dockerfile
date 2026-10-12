# Stage 1: Build the application using Java 21 JDK and Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml to cache dependency downloads
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B || true

# Copy source code and package the Spring Boot JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application using Java 21 runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the generated Spring Boot JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Create default uploads directory
RUN mkdir -p /app/uploads

# Configure default port (defaults to 8080)
ENV PORT=8080
EXPOSE 8080

# Make the application listen on the port provided by the PORT environment variable
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
