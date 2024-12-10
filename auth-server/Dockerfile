# Build stage
FROM maven:3.8-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM openjdk:17-slim
WORKDIR /app

# Install PostgreSQL and Redis
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
    postgresql \
    postgresql-contrib \
    redis-server \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Get PostgreSQL version and setup directories
RUN PG_VERSION=$(ls /usr/lib/postgresql/) && \
    mkdir -p /var/run/postgresql && \
    chown postgres:postgres /var/run/postgresql && \
    mkdir -p /var/lib/postgresql/data && \
    chown postgres:postgres /var/lib/postgresql/data && \
    mkdir -p /etc/postgresql/${PG_VERSION}/main && \
    chown postgres:postgres /etc/postgresql/${PG_VERSION}/main

# Initialize PostgreSQL database
USER postgres
RUN PG_VERSION=$(ls /usr/lib/postgresql/) && \
    /usr/lib/postgresql/${PG_VERSION}/bin/initdb -D /var/lib/postgresql/data
USER root

# Configure PostgreSQL
RUN PG_VERSION=$(ls /usr/lib/postgresql/) && \
    echo "host all all 0.0.0.0/0 md5" >> /etc/postgresql/${PG_VERSION}/main/pg_hba.conf && \
    echo "listen_addresses='*'" >> /etc/postgresql/${PG_VERSION}/main/postgresql.conf

# Copy application files
COPY --from=build /app/target/*.jar app.jar
COPY redis.conf /etc/redis/redis.conf
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh

# Create log directory
RUN mkdir -p /var/log/postgresql && \
    chown postgres:postgres /var/log/postgresql

EXPOSE 8081 6379 5432
ENTRYPOINT ["/app/start.sh"]

