CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(20) PRIMARY KEY,
    user_token VARCHAR(88),
    daily_traffic_limit VARCHAR(8),
    daily_traffic_usage VARCHAR(8),
    expiration_date VARCHAR(8),
    is_member BOOLEAN DEFAULT false
);
