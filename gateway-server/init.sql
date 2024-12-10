CREATE TABLE routing_rules (
    id BIGSERIAL PRIMARY KEY,
    route_id VARCHAR(255) NOT NULL UNIQUE,
    predicates TEXT NOT NULL,
    uri TEXT NOT NULL,
    filters TEXT,
    priority INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_updated TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT unique_route_id UNIQUE (route_id)
);

CREATE INDEX idx_route_id ON routing_rules(route_id);
CREATE INDEX idx_priority ON routing_rules(priority);
