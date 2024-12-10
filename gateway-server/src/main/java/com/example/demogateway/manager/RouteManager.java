package com.example.demogateway.manager;

import com.example.demogateway.model.RouteUpdateMessage;
import com.example.demogateway.model.RouteAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class RouteManager {
    private final Map<String, RouteDefinition> localRoutes = new ConcurrentHashMap<>();
    private final RouteDefinitionWriter routeDefinitionWriter;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer messageListenerContainer;
    private final ApplicationEventPublisher eventPublisher;
    private static final String ROUTE_CHANNEL = "route:updates";
    private static final String ROUTE_KEY_PREFIX = "route:definition:";
    private static final Logger log = LoggerFactory.getLogger(RouteManager.class);

    public RouteManager(RedisConnectionFactory connectionFactory,
                       RouteDefinitionWriter routeDefinitionWriter,
                       RedisTemplate<String, String> redisTemplate,
                       ObjectMapper objectMapper,
                       ApplicationEventPublisher eventPublisher) {
        this.routeDefinitionWriter = routeDefinitionWriter;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;

        this.messageListenerContainer = new RedisMessageListenerContainer();
        this.messageListenerContainer.setConnectionFactory(connectionFactory);

        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(1);
        taskExecutor.setMaxPoolSize(10);
        taskExecutor.setThreadNamePrefix("RedisListener-");
        taskExecutor.initialize();
        this.messageListenerContainer.setTaskExecutor(taskExecutor);
        this.messageListenerContainer.setSubscriptionExecutor(taskExecutor);
    }

    @PostConstruct
    public void init() {
        messageListenerContainer.addMessageListener(
                (message, pattern) -> {
                    try {
                        RouteUpdateMessage updateMsg = objectMapper.readValue(
                                new String(message.getBody()), RouteUpdateMessage.class);
                        handleRouteUpdate(updateMsg);
                    } catch (Exception e) {
                        log.error("Error processing route update", e);
                    }
                },
                new PatternTopic(ROUTE_CHANNEL)
        );
        messageListenerContainer.start();
        loadRoutesFromRedis();
        log.info("Route manager initialized and listening for updates");
    }

    private void loadRoutesFromRedis() {
        try {
            Set<String> keys = redisTemplate.keys(ROUTE_KEY_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    String json = redisTemplate.opsForValue().get(key);
                    if (json != null) {
                        RouteDefinition route = objectMapper.readValue(json, RouteDefinition.class);
                        localRoutes.put(route.getId(), route);
                        routeDefinitionWriter.save(Mono.just(route)).subscribe();
                    }
                }
                refreshRoutes();
            }
        } catch (Exception e) {
            log.error("Error loading routes from Redis", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        messageListenerContainer.stop();
        log.info("Route manager stopped");
    }

    public Mono<RouteDefinition> addRoute(RouteDefinition route) {
        log.info("Starting route addition process for route ID: {}", route.getId());
        
        return Mono.just(route)
                .doOnNext(r -> {
                    log.info("Received route definition: {}", r);
                    try {
                        String json = objectMapper.writeValueAsString(r);
                        redisTemplate.opsForValue().set(ROUTE_KEY_PREFIX + r.getId(), json);
                    } catch (JsonProcessingException e) {
                        log.error("Error saving route to Redis", e);
                    }
                })
                .flatMap(r -> routeDefinitionWriter.save(Mono.just(r))
                        .doOnSubscribe(subscription -> log.info("Starting to save route: {}", r.getId()))
                        .then(Mono.just(r)))
                .doOnSuccess(r -> {
                    localRoutes.put(r.getId(), r);
                    log.info("Route added to local cache: {}", r.getId());
                    try {
                        publishRouteUpdate(new RouteUpdateMessage(RouteAction.ADD, r, System.currentTimeMillis()));
                        refreshRoutes();
                    } catch (Exception e) {
                        log.error("Error publishing route update", e);
                    }
                })
                .doOnError(error -> log.error("Error saving route: {}", error.getMessage()));
    }

    public Mono<Void> deleteRoute(String routeId) {
        return Mono.defer(() -> {
            String redisKey = ROUTE_KEY_PREFIX + routeId;
            Boolean exists = redisTemplate.hasKey(redisKey);
            
            if (Boolean.TRUE.equals(exists)) {
                redisTemplate.delete(redisKey);
                return routeDefinitionWriter.delete(Mono.just(routeId))
                        .doOnSuccess(v -> {
                            localRoutes.remove(routeId);
                            try {
                                publishRouteUpdate(new RouteUpdateMessage(
                                        RouteAction.DELETE,
                                        routeId,
                                        System.currentTimeMillis()
                                ));
                                refreshRoutes();
                            } catch (Exception e) {
                                log.error("Error publishing route deletion", e);
                            }
                        });
            } else {
                log.warn("Route not found in Redis: {}", routeId);
                return Mono.error(new RuntimeException("Route not found: " + routeId));
            }
        });
    }

    private void publishRouteUpdate(RouteUpdateMessage message) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(message);
        redisTemplate.convertAndSend(ROUTE_CHANNEL, json);
        log.debug("Published route update: {}", json);
    }

    private void handleRouteUpdate(RouteUpdateMessage message) {
        try {
            switch (message.getAction()) {
                case ADD:
                    RouteDefinition route = objectMapper.convertValue(
                            message.getPayload(), RouteDefinition.class);
                    routeDefinitionWriter.save(Mono.just(route))
                            .doOnSuccess(v -> {
                                localRoutes.put(route.getId(), route);
                                refreshRoutes();
                            })
                            .subscribe();
                    log.info("Route synchronized: {}", route.getId());
                    break;
                case DELETE:
                    String routeId = (String) message.getPayload();
                    routeDefinitionWriter.delete(Mono.just(routeId))
                            .doOnSuccess(v -> {
                                localRoutes.remove(routeId);
                                refreshRoutes();
                            })
                            .subscribe();
                    log.info("Route removal synchronized: {}", routeId);
                    break;
                default:
                    log.warn("Unknown route action: {}", message.getAction());
            }
        } catch (Exception e) {
            log.error("Error handling route update", e);
        }
    }

    private void refreshRoutes() {
        log.info("Triggering route refresh");
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
    }

    public Map<String, RouteDefinition> getRoutes() {
        return new ConcurrentHashMap<>(localRoutes);
    }
}
