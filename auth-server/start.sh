#!/bin/bash
set -e

# Initialize variables from environment
REDIS_MODE=${REDIS_MODE:-"master"}
REDIS_PASSWORD=${REDIS_PASSWORD:-"1234"}
PG_VERSION=$(ls /usr/lib/postgresql/)
PG_DATA="/var/lib/postgresql/data"

# Ensure PostgreSQL data directory has correct permissions
chown -R postgres:postgres $PG_DATA
chmod 700 $PG_DATA

# Update pg_hba.conf
cat > "$PG_DATA/pg_hba.conf" <<EOF
# TYPE  DATABASE        USER            ADDRESS                 METHOD
local   all            postgres                                trust
local   all            all                                     trust
host    all            all             127.0.0.1/32            md5
host    all            all             ::1/128                 md5
host    all            all             0.0.0.0/0               md5
host    all            all             ::/0                    md5
EOF

chown postgres:postgres "$PG_DATA/pg_hba.conf"
chmod 600 "$PG_DATA/pg_hba.conf"

# Ensure postgresql.conf has proper listen settings
echo "listen_addresses = '*'" >> "$PG_DATA/postgresql.conf"
chown postgres:postgres "$PG_DATA/postgresql.conf"

# Start PostgreSQL
echo "Starting PostgreSQL..."
su - postgres -c "/usr/lib/postgresql/${PG_VERSION}/bin/pg_ctl -D $PG_DATA start"

# Wait for PostgreSQL to be ready
until su - postgres -c "psql -c '\l'" >/dev/null 2>&1; do
    echo "Waiting for PostgreSQL to start..."
    sleep 1
done

# Configure PostgreSQL
echo "Configuring PostgreSQL..."
su - postgres -c "psql -c \"ALTER USER postgres WITH PASSWORD '1234';\""
su - postgres -c "psql -c \"CREATE DATABASE authdb;\"" || true
su - postgres -c "psql -c \"GRANT ALL PRIVILEGES ON DATABASE authdb TO postgres;\"" || true

# Set up Redis directories and permissions
mkdir -p /data /var/lib/redis
chown -R redis:redis /data /var/lib/redis
chmod 750 /data /var/lib/redis

# Start Redis
echo "Starting Redis..."
if [ "$REDIS_MODE" = "master" ]; then
    redis-server /etc/redis/redis.conf --bind 0.0.0.0 &
elif [ "$REDIS_MODE" = "slave" ]; then
    # Use existing slave configuration file
    redis-server /etc/redis/redis.conf &
fi

# Wait for Redis
until redis-cli -a "$REDIS_PASSWORD" ping > /dev/null 2>&1; do
    echo "Waiting for Redis to start..."
    sleep 1
done

# Configure sentinel.conf
if [ ! -z "$REDIS_SENTINEL_PORT" ]; then
    cat > /etc/redis/sentinel.conf <<EOF
port 26379
dir "/var/lib/redis"
sentinel monitor auth-master gateway-service-1 6379 1
sentinel auth-pass auth-master 1234
sentinel down-after-milliseconds auth-master 5000
sentinel failover-timeout auth-master 60000
sentinel parallel-syncs auth-master 1
protected-mode no
EOF

    chown redis:redis /etc/redis/sentinel.conf
    chmod 640 /etc/redis/sentinel.conf

    echo "Starting Redis Sentinel..."
    redis-sentinel /etc/redis/sentinel.conf --bind 0.0.0.0 &
fi

echo "Redis and PostgreSQL are ready!"

# Start the Spring application
exec java -jar app.jar
