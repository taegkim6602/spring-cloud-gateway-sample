package com.example.demogateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "redis.cache")
public class RedisCacheProperties {
    private long timeToLive = 3600; // Default 1 hour
    private boolean enableWarmUp = true;
    private int warmUpRetryCount = 3;
    private long warmUpRetryDelay = 5000; // 5 seconds
    private String cachePrefix = "route:";
    private String lockKey = "cache_warming_lock";
    private long lockTimeout = 30000; // 30 seconds
    private int batchSize = 1000; // Batch size for loading data
    private long healthCheckInterval = 300000; // 5 minutes
    private int expirationWarningThreshold = 300; // 5 minutes
}
