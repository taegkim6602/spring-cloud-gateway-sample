package com.example.demogateway.config;

import io.lettuce.core.ReadFrom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Arrays;

@Configuration
public class RedisGatewayConfig {

    private static final Logger logger = LoggerFactory.getLogger(RedisGatewayConfig.class);

    @Value("${redis.mode:master}")
    private String redisMode;

    @Value("${spring.redis.password}")
    private String redisPassword;

    @Value("${spring.redis.sentinel.master:#{null}}")
    private String sentinelMaster;

    @Value("${spring.redis.sentinel.nodes:#{null}}")
    private String sentinelNodes;

    @PostConstruct
    public void testConnection() {
        logger.info("Testing Redis Sentinel connection...");
        logger.info("Sentinel master: {}", sentinelMaster);
        logger.info("Sentinel nodes: {}", sentinelNodes);

        String[] nodes = sentinelNodes.split(",");
        for (String node : nodes) {
            String[] hostPort = node.trim().split(":");
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);

            try (Socket socket = new Socket()) {
                logger.info("Attempting to connect to sentinel {}:{}", host, port);
                socket.connect(new InetSocketAddress(host, port), 3000);
                logger.info("Successfully connected to sentinel {}:{}", host, port);

                // Try Redis CLI command
                Process process = Runtime.getRuntime().exec(
                        String.format("redis-cli -h %s -p %d -a %s ping",
                                host, port, redisPassword)
                );
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    logger.info("Redis CLI response from {}:{}: {}", host, port, line);
                }
            } catch (Exception e) {
                logger.error("Failed to connect to sentinel {}:{}", host, port, e);
            }
        }
    }

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration()
                .master(sentinelMaster);

        // Configure sentinels
        Arrays.stream(sentinelNodes.split(","))
                .map(node -> {
                    String[] parts = node.trim().split(":");
                    return new RedisNode(parts[0], Integer.parseInt(parts[1]));
                })
                .forEach(sentinel -> {
                    sentinelConfig.sentinel(sentinel);
                    logger.info("Added sentinel: {}:{}", sentinel.getHost(), sentinel.getPort());
                });

        // Set passwords
        sentinelConfig.setPassword(redisPassword);
        sentinelConfig.setSentinelPassword(redisPassword);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .readFrom(ReadFrom.MASTER_PREFERRED)
                .commandTimeout(Duration.ofSeconds(20))
                .build();

        return new LettuceConnectionFactory(sentinelConfig, clientConfig);
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

