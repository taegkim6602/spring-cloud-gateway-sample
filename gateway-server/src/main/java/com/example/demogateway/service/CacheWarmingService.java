package com.example.demogateway.service;

import com.example.demogateway.entity.RoutingRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;
import java.net.URI;
import java.util.ArrayList;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class CacheWarmingService {

    private static final Logger logger = LoggerFactory.getLogger(CacheWarmingService.class);
    private static final String ROUTE_CACHE_PREFIX = "route:";

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheWarmingService(JdbcTemplate jdbcTemplate,
                               RedisTemplate<String, String> redisTemplate,
                               ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void warmupCache() {
        logger.info("Starting initial cache warmup");
        loadRoutesToCache();
    }

    @Transactional(readOnly = true)
    public void loadRoutesToCache() {
        try {
            jdbcTemplate.setFetchSize(1000);
            List<RoutingRule> rules = jdbcTemplate.query(
                    "SELECT * FROM routing_rules WHERE is_active = true ORDER BY priority DESC",
                    (rs, rowNum) -> {
                        RoutingRule rule = new RoutingRule();
                        rule.setRouteId(rs.getString("route_id"));
                        rule.setPredicates(rs.getString("predicates"));
                        rule.setUri(rs.getString("uri"));
                        rule.setFilters(rs.getString("filters"));
                        rule.setPriority(rs.getInt("priority"));
                        rule.setIsActive(rs.getBoolean("is_active"));
                        return rule;
                    }
            );

            redisTemplate.executePipelined((RedisCallback<?>) (connection) -> {
                for (RoutingRule rule : rules) {
                    try {
                        RouteDefinition routeDefinition = convertToRouteDefinition(rule);
                        String routeJson = objectMapper.writeValueAsString(routeDefinition);
                        String cacheKey = ROUTE_CACHE_PREFIX + rule.getRouteId();

                        connection.stringCommands().set(
                                cacheKey.getBytes(),
                                routeJson.getBytes()
                        );
                    } catch (Exception e) {
                        logger.error("Error caching route {}: {}", rule.getRouteId(), e.getMessage());
                    }
                }
                return null;
            });

            logger.info("Successfully cached {} routes at startup", rules.size());
        } catch (Exception e) {
            logger.error("Error during cache warmup: {}", e.getMessage(), e);
        }
    }

    private RouteDefinition convertToRouteDefinition(RoutingRule rule) throws JsonProcessingException {
        RouteDefinition routeDefinition = new RouteDefinition();
        routeDefinition.setId(rule.getRouteId());
        routeDefinition.setUri(URI.create(rule.getUri()));

        // Convert predicates from JSON string to List<PredicateDefinition>
        List<PredicateDefinition> predicates = objectMapper.readValue(
                rule.getPredicates(),
                new TypeReference<List<PredicateDefinition>>() {}
        );
        routeDefinition.setPredicates(predicates);

        // Convert filters from JSON string to List<FilterDefinition> if exists
        if (rule.getFilters() != null && !rule.getFilters().isEmpty()) {
            List<FilterDefinition> filters = objectMapper.readValue(
                    rule.getFilters(),
                    new TypeReference<List<FilterDefinition>>() {}
            );
            routeDefinition.setFilters(filters);
        } else {
            routeDefinition.setFilters(new ArrayList<>());
        }

        return routeDefinition;
    }
}