package com.example.demogateway.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.data.redis.core.RedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RedisRouteDefinitionRepository implements RouteDefinitionRepository {
    private static final String ROUTE_KEY_PREFIX = "route:definition:";
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRouteDefinitionRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(redisTemplate.keys(ROUTE_KEY_PREFIX + "*"))
                .map(key -> {
                    try {
                        String json = redisTemplate.opsForValue().get(key);
                        return objectMapper.readValue(json, RouteDefinition.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Error deserializing route definition", e);
                    }
                });
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(r -> {
            try {
                String json = objectMapper.writeValueAsString(r);
                redisTemplate.opsForValue().set(ROUTE_KEY_PREFIX + r.getId(), json);
                return Mono.empty();
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id -> {
            redisTemplate.delete(ROUTE_KEY_PREFIX + id);
            return Mono.empty();
        });
    }
}
