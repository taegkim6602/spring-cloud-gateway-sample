CREATE TABLE IF NOT EXISTS routing_rules (
    id BIGSERIAL PRIMARY KEY,
    route_id VARCHAR(255) NOT NULL UNIQUE,
    predicates TEXT NOT NULL,
    uri TEXT NOT NULL,
    filters TEXT,
    priority INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_route_id UNIQUE (route_id)
);

CREATE INDEX IF NOT EXISTS idx_route_id ON routing_rules(route_id);
CREATE INDEX IF NOT EXISTS idx_priority ON routing_rules(priority);

-- Insert sample route
INSERT INTO routing_rules (
    route_id, 
    predicates, 
    uri, 
    filters, 
    priority, 
    is_active
) VALUES (
    'example_route',
    '[{"name":"Path","args":{"pattern":"/example/**"}}]',
    'http://example.com',
    '[{"name":"StripPrefix","args":{"parts":"1"}}]',
    1,
    true
) ,
 (
             'dummy_route',
             '[{"name":"Path","args":{"_genkey_0":"/dummy_get/**"}}]',
             'https://dummyjson.com',
             '[{"name":"RewritePath","args":{"_genkey_0":"/dummy_get/(?<segment>.*)","_genkey_1":"/${segment}"}}]',
             1,
             true
         ) ON CONFLICT (route_id) DO NOTHING;
