/*
package com.example.demogateway.manager;

import com.example.demogateway.model.RouteUpdateMessage;
import com.example.demogateway.model.RouteAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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


import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RouteManager {
    private final Map<String, RouteDefinition> localRoutes = new ConcurrentHashMap<>();
    private final RouteDefinitionWriter routeDefinitionWriter;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer messageListenerContainer;
    private static final String ROUTE_CHANNEL = "route:updates";
    private static final Logger log = LoggerFactory.getLogger(RouteManager.class);


    public RouteManager(RedisConnectionFactory connectionFactory,
                        RouteDefinitionWriter routeDefinitionWriter,
                        RedisTemplate<String, String> redisTemplate,
                        ObjectMapper objectMapper) {
        this.routeDefinitionWriter = routeDefinitionWriter;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;

        // Initialize Redis message listener container
        this.messageListenerContainer = new RedisMessageListenerContainer();
        this.messageListenerContainer.setConnectionFactory(connectionFactory);

        // Configure task executor
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
        log.info("Route manager initialized and listening for updates");
    }

    @PreDestroy
    public void cleanup() {
        messageListenerContainer.stop();
        log.info("Route manager stopped");
    }




    public void addRoute(RouteDefinition route) {

        log.info("Received route definition: {}", route);  // Add this line
        log.info("Route ID: {}", route.getId());
        log.info("Route URI: {}", route.getUri());
        log.info("Route Predicates: {}", route.getPredicates());

        try {
            URI uri = route.getUri();
            log.info("URI scheme: {}, host: {}, path: {}", uri.getScheme(), uri.getHost(), uri.getPath());
        } catch (Exception e) {
            log.error("Invalid URI format: {}", route.getUri());
        }

        try {
            //routeDefinitionWriter.save(Mono.just(route)).subscribe();
            //localRoutes.put(route.getId(), route);

            routeDefinitionWriter.save(Mono.just(route))
                    .doOnSubscribe(subscription ->
                            log.info("Starting to save route: {}", route.getId()))
                    .doOnSuccess(unused ->
                            log.info("Route successfully saved to RouteDefinitionWriter: {}", route.getId()))
                    .doOnError(error ->
                            log.error("Error saving route to RouteDefinitionWriter: {}", error.getMessage()))
                    .subscribe(
                            unused -> {
                                log.info("Route save operation completed successfully: {}", route.getId());
                                localRoutes.put(route.getId(), route);
                                try {
                                    RouteUpdateMessage message = new RouteUpdateMessage(
                                            RouteAction.ADD,
                                            route,
                                            System.currentTimeMillis()
                                    );
                                    publishRouteUpdate(message);
                                    log.info("Route update published to Redis: {}", route.getId());
                                } catch (Exception e) {
                                    log.error("Failed to publish route update to Redis: {}", e.getMessage());
                                }
                            },
                            error -> log.error("Route save operation failed: {}", error.getMessage()),
                            () -> log.info("Route save operation completed: {}", route.getId())
                    );

            // Verify route was added to local cache
            if (localRoutes.containsKey(route.getId())) {
                log.info("Route successfully added to local cache: {}", route.getId());
            } else {
                log.warn("Route not found in local cache after save: {}", route.getId());
            }



            RouteUpdateMessage message = new RouteUpdateMessage(
                    RouteAction.ADD,
                    route,
                    System.currentTimeMillis()
            );
            publishRouteUpdate(message);
            log.info("Route added: {}", route.getId());
        } catch (Exception e) {
            log.error("Error adding route: {}", route.getId(), e);
            throw new RuntimeException("Failed to add route", e);
        }
    }

    public void deleteRoute(String routeId) {
        try {
            // Check if route exists first
            if (!localRoutes.containsKey(routeId)) {
                log.warn("Route not found for deletion: {}", routeId);
                throw new RuntimeException("Route not found: " + routeId);
            }

            // Delete from local cache first
            localRoutes.remove(routeId);

            // Then try to delete from the gateway
            routeDefinitionWriter.delete(Mono.just(routeId))
                    .subscribe(
                            success -> log.debug("Route successfully deleted from gateway: {}", routeId),
                            error -> log.error("Error deleting route from gateway: {}", routeId, error),
                            () -> log.debug("Delete operation completed for route: {}", routeId)
                    );

            // Publish update to other instances
            RouteUpdateMessage message = new RouteUpdateMessage(
                    RouteAction.DELETE,
                    routeId,
                    System.currentTimeMillis()
            );
            publishRouteUpdate(message);
            log.info("Route deleted: {}", routeId);
        } catch (Exception e) {
            log.error("Error deleting route: {}", routeId, e);
            throw new RuntimeException("Failed to delete route: " + routeId, e);
        }
    }

    private void publishRouteUpdate(RouteUpdateMessage message) throws JsonProcessingException {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(ROUTE_CHANNEL, json);
            log.debug("Published route update: {}", json);
        } catch (Exception e) {
            log.error("Error publishing route update", e);
            throw e;
        }
    }

    private void handleRouteUpdate(RouteUpdateMessage message) {
        try {
            switch (message.getAction()) {
                case ADD:
                    RouteDefinition route = objectMapper.convertValue(
                            message.getPayload(), RouteDefinition.class);
                    routeDefinitionWriter.save(Mono.just(route)).subscribe();
                    localRoutes.put(route.getId(), route);
                    log.info("Route synchronized: {}", route.getId());
                    break;
                case DELETE:
                    String routeId = (String) message.getPayload();
                    routeDefinitionWriter.delete(Mono.just(routeId)).subscribe();
                    localRoutes.remove(routeId);
                    log.info("Route removal synchronized: {}", routeId);
                    break;
                default:
                    log.warn("Unknown route action: {}", message.getAction());
            }
        } catch (Exception e) {
            log.error("Error handling route update", e);
        }
    }

    public Map<String, RouteDefinition> getRoutes() {
        return new ConcurrentHashMap<>(localRoutes);
    }
}*/
