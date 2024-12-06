/*
package com.example.demogateway.controller;


import com.example.demogateway.manager.RouteManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/routes")
public class RouteController {
    private static final Logger log = LoggerFactory.getLogger(RouteController.class);
    private final ObjectMapper objectMapper;  // Add this
    private final RouteManager routeManager;

    public RouteController(RouteManager routeManager, ObjectMapper objectMapper) {
        this.routeManager = routeManager;
        this.objectMapper = objectMapper;     // Add this
        log.info("RouteController initialized");
    }

    @PostMapping
    public Mono<ResponseEntity<Object>> addRoute(@RequestBody String routeJson) {  // Change to String
        log.info("Received route JSON: {}", routeJson);

        try {
            RouteDefinition route = objectMapper.readValue(routeJson, RouteDefinition.class);
            log.info("Parsed route: id={}, uri={}", route.getId(), route.getUri());

            return Mono.just(route)
                    .doOnNext(r -> log.info("Processing route: {}", r.getId()))
                    .doOnNext(routeManager::addRoute)
                    .thenReturn(ResponseEntity.ok().build())
                    .onErrorResume(e -> {
                        log.error("Error processing route: {}", e.getMessage(), e);
                        return Mono.just(ResponseEntity.badRequest().build());
                    });
        } catch (Exception e) {
            log.error("Error parsing route definition: {}", e.getMessage(), e);
            return Mono.just(ResponseEntity.badRequest().build());
        }
    }





}
*/
