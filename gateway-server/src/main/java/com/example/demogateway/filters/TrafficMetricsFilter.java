package com.example.demogateway.filters;

import com.example.demogateway.util.PathNormalizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class TrafficMetricsFilter implements GlobalFilter {
    private static final Logger logger = LoggerFactory.getLogger(TrafficMetricsFilter.class);
    private final MeterRegistry meterRegistry;
    private final PathNormalizer pathNormalizer;
    
    @Value("${metrics.percentiles:0.5,0.95,0.99}")
    private double[] percentiles;
    
    @Value("${metrics.sla-boundaries:0.1,0.5,1.0,2.0}")
    private double[] slaBoundaries;

    public TrafficMetricsFilter(MeterRegistry meterRegistry, PathNormalizer pathNormalizer) {
        this.meterRegistry = meterRegistry;
        this.pathNormalizer = pathNormalizer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant startTime = Instant.now();
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "unknown";
        
        String originalPath = exchange.getRequest().getPath().value();
        String normalizedPath = pathNormalizer.normalizePath(originalPath, routeId);
        String method = exchange.getRequest().getMethod().name();

        return chain.filter(exchange)
            .doOnError(throwable -> {
                recordError(exchange, throwable, routeId);
                logger.error("Error processing request: {}", throwable.getMessage(), throwable);
            })
            .doFinally(signalType -> {
                try {
                    recordMetrics(exchange, startTime, normalizedPath, method, routeId);
                } catch (Exception e) {
                    logger.error("Error recording metrics: {}", e.getMessage(), e);
                }
            });
    }

    private Timer buildRequestTimer(String routeId, String method, String path, String status) {
      return Timer.builder("gateway.request.duration")
          .description("Request duration tracking with percentiles and SLA boundaries")
          .tag("routeId", routeId)
          .tag("method", method)
          .tag("path", path)
          .tag("status", status)
          // Use SLO buckets for histograms
          .sla(
              Duration.ofSeconds(1),
              Duration.ofSeconds(2),
              Duration.ofSeconds(5)
          )
          .publishPercentiles(percentiles)
          .publishPercentileHistogram()
          .minimumExpectedValue(Duration.ofMillis(1))
          .maximumExpectedValue(Duration.ofSeconds(10))
          .percentilePrecision(2)
          .register(meterRegistry);
      }

    private void recordError(ServerWebExchange exchange, Throwable throwable, String routeId) {
        String errorType = throwable.getClass().getSimpleName();
        meterRegistry.counter("gateway.errors",
                "routeId", routeId,
                "errorType", errorType)
                .increment();
    }

    private void recordMetrics(ServerWebExchange exchange, Instant startTime, 
                             String path, String method, String routeId) {
        long requestDuration = Duration.between(startTime, Instant.now()).toMillis();
        HttpStatus status = exchange.getResponse().getStatusCode();
        String statusCode = status != null ? String.valueOf(status.value()) : "unknown";

        // Record request timing
        Timer requestTimer = buildRequestTimer(routeId, method, path, statusCode);
        requestTimer.record(Duration.ofMillis(requestDuration));

        // Record request count
        meterRegistry.counter("gateway.requests",
                "routeId", routeId,
                "method", method,
                "path", path,
                "status", statusCode)
                .increment();

        // Record response size
        Long contentLength = exchange.getResponse().getHeaders().getContentLength();
        // Record response size with logging
        logger.info("Response size for path: {}, method: {}, routeId: {}, content length: {}", 
                path, method, routeId, contentLength);

        if (contentLength != null && contentLength > 0) {
            logger.info("Recording traffic metric - Path: {}, Method: {}, RouteId: {}, Size: {} bytes",
                       path, method, routeId, contentLength);
            
            try {
                meterRegistry.counter("gateway.traffic.bytes",
                        "routeId", routeId,
                        "method", method,
                        "path", path)
                        .increment(contentLength);
                logger.info("Successfully recorded traffic metric");
            } catch (Exception e) {
                logger.error("Failed to record traffic metric: {}", e.getMessage(), e);
            }
          } else {
              logger.warn("No content length available for path: {}, method: {}, routeId: {}", 
                         path, method, routeId);
          }

        // Record SLA violations
        if (requestDuration > TimeUnit.SECONDS.toMillis(2)) {
            meterRegistry.counter("gateway.sla.violations",
                    "routeId", routeId,
                    "method", method,
                    "path", path,
                    "sla", "2s")
                    .increment();
        }
    }
}
