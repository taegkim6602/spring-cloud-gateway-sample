#!/bin/bash

# Start PostgreSQL service with specific data directory
PG_VERSION=$(ls /usr/lib/postgresql/)
su - postgres -c "/usr/lib/postgresql/${PG_VERSION}/bin/pg_ctl -D /var/lib/postgresql/data start"

# Wait for PostgreSQL to be ready
until pg_isready; do
    echo "Waiting for PostgreSQL to start..."
    sleep 1
done

# Configure PostgreSQL
su - postgres -c "psql -c \"ALTER USER postgres WITH PASSWORD '1234';\""
su - postgres -c "psql -c \"CREATE DATABASE authdb;\"" || true
su - postgres -c "psql -c \"GRANT ALL PRIVILEGES ON DATABASE authdb TO postgres;\"" || true

# Start Redis server in the background
redis-server /etc/redis/redis.conf &

# Wait for Redis to be ready
while ! redis-cli ping > /dev/null 2>&1; do
    echo "Waiting for Redis to start..."
    sleep 1
done

echo "Redis and PostgreSQL are ready!"

# Start the Spring application
exec java -jar app.jar

