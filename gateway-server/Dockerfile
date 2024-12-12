# Build stage
FROM maven:3.8-openjdk-17 AS build
WORKDIR /app

# Copy only pom.xml first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM openjdk:17-slim
WORKDIR /app

# Install Redis, PostgreSQL client and other dependencies
RUN apt-get update && \
    apt-get install -y \
    redis-server \
    postgresql-client \
    netcat-openbsd \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create necessary directories
RUN mkdir -p /etc/redis /var/log /data/redis

# Copy Redis configuration
COPY ./redis-config/redis.conf /etc/redis/redis.conf

# Copy application files
COPY --from=build /app/target/*.jar app.jar
COPY ./src/main/resources/schema.sql /app/schema.sql

# Copy startup script
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh

# Expose ports for the application and Redis
EXPOSE 8080 6379

# Use the startup script as entrypoint
ENTRYPOINT ["/app/start.sh"]
