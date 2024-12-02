package com.example.demogateway.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Map;

@Component
public class ResponseRowCounterFilter implements GlobalFilter, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(ResponseRowCounterFilter.class);
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public ResponseRowCounterFilter(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        logger.info("ResponseRowCounterFilter initialized");
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        logger.info("ResponseRowCounterFilter - Starting to process request: {}",
                exchange.getRequest().getPath().value());

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "unknown";
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                logger.info("ResponseRowCounterFilter - WriteWith called");

                if (body instanceof Flux) {
                    logger.info("ResponseRowCounterFilter - Processing Flux body");
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        logger.info("ResponseRowCounterFilter - Processing response data");
                        DataBuffer joinedBuffers = exchange.getResponse().bufferFactory()
                                .join(dataBuffers);
                        byte[] content = new byte[joinedBuffers.readableByteCount()];
                        joinedBuffers.read(content);
                        DataBufferUtils.release(joinedBuffers);

                        try {
                            // Log raw content for debugging
                            String rawContent = new String(content);
                            logger.debug("Raw response content: {}", rawContent);

                            // Parse the response as JsonNode first
                            JsonNode jsonNode = objectMapper.readTree(content);
                            int rowCount = calculateRowCount(jsonNode);

                            logger.info("ResponseRowCounterFilter - Processed response with {} rows", rowCount);

                            // Add row count to response headers
                            exchange.getResponse().getHeaders().add("X-Row-Count", String.valueOf(rowCount));

                            // Record metrics
                            meterRegistry.gauge("gateway.response.rows",
                                    Tags.of(
                                            "routeId", routeId,
                                            "method", method,
                                            "path", path
                                    ),
                                    rowCount);

                            return exchange.getResponse().bufferFactory().wrap(content);
                        } catch (Exception e) {
                            logger.error("Error processing response body: {}", e.getMessage());
                            logger.debug("Problematic content: {}", new String(content));
                            return exchange.getResponse().bufferFactory().wrap(content);
                        }
                    }));
                } else {
                    logger.info("ResponseRowCounterFilter - Body is not a Flux");
                    return super.writeWith(body);
                }
            }
        };

        return chain.filter(exchange.mutate().response(responseDecorator).build());
    }

    private int calculateRowCount(JsonNode jsonNode) {
        if (jsonNode.isArray()) {
            // If it's directly an array, return its size
            return jsonNode.size();
        } else {
            // If it's an object, look for array fields
            Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value.isArray()) {
                    logger.info("Found array in field: {}", entry.getKey());
                    return value.size();
                } else if (value.isObject()) {
                    // Recursively search for arrays in nested objects
                    int nestedCount = calculateRowCount(value);
                    if (nestedCount > 0) {
                        return nestedCount;
                    }
                }
            }
        }
        return 0;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}