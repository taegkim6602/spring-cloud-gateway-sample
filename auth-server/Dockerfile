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

# Install Redis
RUN apt-get update && \
    apt-get install -y redis-server && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*


COPY --from=build /app/target/*.jar app.jar
COPY redis.conf /etc/redis/redis.conf
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh

EXPOSE 8081 6379
ENTRYPOINT ["/app/start.sh"]

