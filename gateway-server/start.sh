#!/bin/bash

# Start Redis server with custom configuration
redis-server /etc/redis/redis.conf &

# Wait for Redis to be ready
while ! nc -z localhost 6379; do
  echo "Waiting for Redis to start..."
  sleep 1
done

echo "Redis is ready!"

# Start the Spring application
java -jar app.jar
