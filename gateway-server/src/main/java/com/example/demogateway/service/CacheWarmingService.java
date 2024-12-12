package com.example.demogateway.service;

import com.example.demogateway.entity.RoutingRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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
    private static final String CACHE_LOCK_KEY = "cache_warming_lock";
    private static final long LOCK_TIMEOUT = 30000; // 30 seconds

    @Value("${redis.mode:master}")
    private String redisMode;

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
        if (!"master".equals(redisMode)) {
            logger.info("Skipping cache warmup on non-master instance");
            return;
        }

        if (!acquireLock()) {
            logger.info("Another instance is already warming the cache");
            return;
        }

        try {
            logger.info("Starting initial cache warmup");
            loadRoutesToCache();
        } finally {
            releaseLock();
        }
    }

    private boolean acquireLock() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
            CACHE_LOCK_KEY,
            "LOCKED",
            java.time.Duration.ofMillis(LOCK_TIMEOUT)
        );
        return Boolean.TRUE.equals(locked);
    }

    private void releaseLock() {
        redisTemplate.delete(CACHE_LOCK_KEY);
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

        List<PredicateDefinition> predicates = objectMapper.readValue(
                rule.getPredicates(),
                new TypeReference<List<PredicateDefinition>>() {}
        );
        routeDefinition.setPredicates(predicates);

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

    @Scheduled(fixedRateString = "${redis.cache.health-check-interval:300000}")
    public void checkCacheHealth() {
        if (!"master".equals(redisMode)) {
            logger.debug("Skipping cache health check on non-master instance");
            return;
        }

        try {
            Long routeCount = (long) redisTemplate.keys(ROUTE_CACHE_PREFIX + "*").size();
            logger.info("Current cache size: {} routes", routeCount);

            // Compare with database count
            Long dbCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM routing_rules WHERE is_active = true",
                Long.class
            );

            if (dbCount == null || dbCount == 0) {
                logger.warn("No active routes found in database");
                return;
            }

            if (routeCount == 0) {
                logger.warn("Cache is empty, initiating warmup");
                warmupCache();
            } else if (routeCount < dbCount) {
                logger.warn("Cache ({}) has fewer entries than database ({}), initiating warmup",
                    routeCount, dbCount);
                warmupCache();
            }

            // Check cache health metrics
            checkCacheMetrics();

        } catch (Exception e) {
            logger.error("Error during cache health check: {}", e.getMessage(), e);
        }
    }

    private void checkCacheMetrics() {
        try {
            // Check Redis memory usage
            String info = redisTemplate.execute((RedisCallback<String>) connection -> 
                new String(String.valueOf(connection.info("memory")))
            );
            
            logger.info("Redis memory info: {}", info);

            // Check cache hit rate
            String stats = redisTemplate.execute((RedisCallback<String>) connection ->
                new String(String.valueOf(connection.info("stats")))
            );
            
            logger.info("Redis stats: {}", stats);

            // Check if any keys are about to expire
            Long expiringCount = redisTemplate.keys(ROUTE_CACHE_PREFIX + "*").stream()
                .filter(key -> redisTemplate.getExpire(key) < 300) // Less than 5 minutes
                .count();

            if (expiringCount > 0) {
                logger.warn("{} routes are close to expiration", expiringCount);
            }

        } catch (Exception e) {
            logger.error("Error checking cache metrics: {}", e.getMessage(), e);
        }
    }

    public void forceCacheRefresh() {
        logger.info("Force refreshing cache");
        if (acquireLock()) {
            try {
                redisTemplate.delete(redisTemplate.keys(ROUTE_CACHE_PREFIX + "*"));
                loadRoutesToCache();
                logger.info("Cache force refresh completed");
            } finally {
                releaseLock();
            }
        } else {
            logger.warn("Could not acquire lock for force cache refresh");
        }
    }

}
