package com.example.demogateway.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PathNormalizer {
    private static final Logger logger = LoggerFactory.getLogger(PathNormalizer.class);
    private static final Map<String, String> COMMON_PATTERNS = new HashMap<>();
    private static final Map<String, Pattern> SERVICE_PATTERNS = new HashMap<>();
    
    @PostConstruct
    private void initialize() {
        // Initialize common patterns
        COMMON_PATTERNS.put("UUID", "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
        COMMON_PATTERNS.put("NUMBER_ID", "\\d+");
        COMMON_PATTERNS.put("ALPHA_NUM_ID", "[a-zA-Z0-9]+");
        COMMON_PATTERNS.put("DATE", "\\d{4}-\\d{2}-\\d{2}");
        
        initializeServicePatterns();
    }

    private void initializeServicePatterns() {
        // User service patterns
        SERVICE_PATTERNS.put("user-service", Pattern.compile(
            "/users/(" + COMMON_PATTERNS.get("NUMBER_ID") + ")" +
            "|/users/(" + COMMON_PATTERNS.get("UUID") + ")/profile" +
            "|/users/search/.*"
        ));

        // Order service patterns
        SERVICE_PATTERNS.put("order-service", Pattern.compile(
            "/orders/(" + COMMON_PATTERNS.get("ALPHA_NUM_ID") + ")" +
            "|/orders/(" + COMMON_PATTERNS.get("UUID") + ")/items" +
            "|/orders/batch/(" + COMMON_PATTERNS.get("DATE") + ")"
        ));

        // Add more service patterns as needed
    }

    public String normalizePath(String path, String serviceId) {
        try {
            String normalizedPath = normalizeServicePath(path, serviceId);
            normalizedPath = normalizeCommonPatterns(normalizedPath);
            normalizedPath = normalizePaginationParams(normalizedPath);
            return normalizeQueryParams(normalizedPath);
        } catch (Exception e) {
            logger.warn("Error normalizing path: {} for service: {}. Using original path.", 
                       path, serviceId, e);
            return path;
        }
    }

    private String normalizeServicePath(String path, String serviceId) {
        Pattern servicePattern = SERVICE_PATTERNS.get(serviceId);
        if (servicePattern != null) {
            switch (serviceId) {
                case "user-service":
                    return path.replaceAll("/users/\\d+", "/users/{id}")
                              .replaceAll("/users/[^/]+/profile", "/users/{id}/profile")
                              .replaceAll("/users/search/.*", "/users/search/{query}");
                case "order-service":
                    return path.replaceAll("/orders/[A-Z0-9-]+", "/orders/{orderId}")
                              .replaceAll("/orders/batch/\\d{4}-\\d{2}-\\d{2}", 
                                        "/orders/batch/{date}");
                default:
                    return path;
            }
        }
        return path;
    }

    private String normalizeCommonPatterns(String path) {
        path = path.replaceAll("/v\\d+/", "/v{version}/");
        path = path.replaceAll("\\d{4}-\\d{2}-\\d{2}", "{date}");
        return path.replaceAll("/\\d+", "/{id}");
    }

    private String normalizePaginationParams(String path) {
        return path.replaceAll("[?&]page=\\d+", "?page={page}")
                  .replaceAll("[?&]size=\\d+", "&size={size}")
                  .replaceAll("[?&]offset=\\d+", "&offset={offset}")
                  .replaceAll("[?&]limit=\\d+", "&limit={limit}");
    }

    private String normalizeQueryParams(String path) {
        String[] parts = path.split("\\?", 2);
        if (parts.length == 1) return path;
        
        String basePath = parts[0];
        String[] params = parts[1].split("&");
        Arrays.sort(params);
        
        List<String> normalizedParams = new ArrayList<>();
        for (String param : params) {
            String normalizedParam = normalizeQueryParam(param);
            if (!normalizedParam.isEmpty()) {
                normalizedParams.add(normalizedParam);
            }
        }
        
        return normalizedParams.isEmpty() ? basePath : 
               basePath + "?" + String.join("&", normalizedParams);
    }

    private String normalizeQueryParam(String param) {
        if (param.startsWith("search=") || param.startsWith("filter=")) {
            return param.split("=")[0] + "={value}";
        }
        if (param.startsWith("sort=")) {
            return "sort={field}";
        }
        return param;
    }
}
