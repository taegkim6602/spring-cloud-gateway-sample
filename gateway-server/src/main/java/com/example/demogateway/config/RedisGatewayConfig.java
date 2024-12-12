package com.example.demogateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RedisGatewayConfig {

    @Value("${redis.mode:master}")
    private String redisMode;

    @Value("${redis.password}")
    private String redisPassword;

    @Value("${spring.redis.sentinel.master:#{null}}")
    private String sentinelMaster;

    @Value("${spring.redis.sentinel.nodes:#{null}}")
    private String sentinelNodes;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        if (sentinelMaster != null && sentinelNodes != null) {
            return createSentinelConnectionFactory();
        } else {
            return createStandaloneConnectionFactory();
        }
    }

    private RedisConnectionFactory createSentinelConnectionFactory() {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration();
        sentinelConfig.setMaster(sentinelMaster);
        sentinelConfig.setSentinels(createSentinels());
        sentinelConfig.setPassword(RedisPassword.of(redisPassword));
        
        return new LettuceConnectionFactory(sentinelConfig);
    }

    private List<RedisNode> createSentinels() {
        List<RedisNode> sentinels = new ArrayList<>();
        for (String node : sentinelNodes.split(",")) {
            String[] parts = node.trim().split(":");
            sentinels.add(new RedisNode(parts[0], Integer.parseInt(parts[1])));
        }
        return sentinels;
    }

    private RedisConnectionFactory createStandaloneConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setPort(redisPort);
        config.setPassword(redisPassword);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @Primary
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(1);
        taskExecutor.setMaxPoolSize(10);
        taskExecutor.setThreadNamePrefix("RedisListener-");
        taskExecutor.initialize();

        container.setTaskExecutor(taskExecutor);
        container.setSubscriptionExecutor(taskExecutor);

        return container;
    }

    @Bean
    @ConditionalOnMissingBean
    @Primary
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate((ReactiveRedisConnectionFactory) connectionFactory);
    }
}

