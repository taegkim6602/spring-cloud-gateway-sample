#!/bin/bash

# Start Redis server with custom configuration
redis-server /etc/redis/redis.conf &

# Wait for Redis to be ready
while ! nc -z localhost 6379; do
  echo "Waiting for Redis to start..."
  sleep 1
done
echo "Redis is ready!"

# Wait for auth-service PostgreSQL to be ready
until PGPASSWORD=1234 pg_isready -h auth-service-1 -U postgres -d authdb
do
    echo "Waiting for auth-service database to be ready..."
    sleep 2
done
echo "Database is ready!"

# Create the routing_rules table
echo "Creating/updating routing_rules table..."
PGPASSWORD=1234 psql -h auth-service-1 -U postgres -d authdb -f /app/schema.sql
if [ $? -eq 0 ]; then
    echo "Database schema updated successfully!"
else
    echo "Warning: Database schema update encountered an issue, but continuing..."
fi

# Start the Spring application
java -jar app.jar




