#!/bin/bash

# Start Redis server in the background
redis-server /etc/redis/redis.conf &

# Wait for Redis to be ready
while ! redis-cli ping > /dev/null 2>&1; do
    echo "Waiting for Redis to start..."
    sleep 1
done

echo "Redis is ready!"

# Start the Spring application
exec java -jar app.jar
