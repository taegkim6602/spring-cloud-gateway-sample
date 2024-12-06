package com.example.demogateway.controller;

import com.example.demogateway.manager.RouteManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/routes")
public class RouteController {
    private static final Logger log = LoggerFactory.getLogger(RouteController.class);
    private final ObjectMapper objectMapper;
    private final RouteManager routeManager;
    private final RouteDefinitionLocator routeDefinitionLocator;

    public RouteController(RouteManager routeManager, 
                          ObjectMapper objectMapper,
                          RouteDefinitionLocator routeDefinitionLocator) {
        this.routeManager = routeManager;
        this.objectMapper = objectMapper;
        this.routeDefinitionLocator = routeDefinitionLocator;
        log.info("RouteController initialized");
    }

    @PostMapping
    public Mono<ResponseEntity<Object>> addRoute(@RequestBody String routeJson) {
        log.info("Received route JSON: {}", routeJson);

        try {
            RouteDefinition route = objectMapper.readValue(routeJson, RouteDefinition.class);
            log.info("Parsed route: id={}, uri={}", route.getId(), route.getUri());

            return routeManager.addRoute(route)
                    .doOnNext(r -> log.info("Route successfully added: {}", r.getId()))
                    .then(Mono.just(ResponseEntity.ok().build()))
                    .onErrorResume(e -> {
                        log.error("Error processing route: {}", e.getMessage(), e);
                        return Mono.just(ResponseEntity.badRequest().build());
                    });
        } catch (Exception e) {
            log.error("Error parsing route definition: {}", e.getMessage(), e);
            return Mono.just(ResponseEntity.badRequest().build());
        }
    }

    @GetMapping("/debug/definitions")
    public Mono<List<RouteDefinition>> getRouteDefinitions() {
        return routeDefinitionLocator.getRouteDefinitions()
                .doOnNext(route -> log.info("Found route: {}", route.getId()))
                .collectList();
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Object>> deleteRoute(@PathVariable String id) {
        return routeManager.deleteRoute(id)
                .then(Mono.just(ResponseEntity.ok().build()))
                .onErrorResume(e -> {
                    log.error("Error deleting route: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @GetMapping("/debug/route/{path}")
    public Mono<Map<String, Object>> testRoute(@PathVariable String path) {
        return routeDefinitionLocator.getRouteDefinitions()
                .filter(route -> route.getPredicates().stream()
                        .anyMatch(pred -> pred.getArgs().values().stream()
                                .anyMatch(arg -> arg.contains(path))))
                .collectMap(
                        RouteDefinition::getId,
                        route -> Map.of(
                                "predicates", route.getPredicates(),
                                "filters", route.getFilters(),
                                "uri", route.getUri()
                        )
                );
    }
}
