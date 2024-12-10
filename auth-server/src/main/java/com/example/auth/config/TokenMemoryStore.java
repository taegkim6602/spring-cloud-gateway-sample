package com.example.auth.config;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class TokenMemoryStore {
    private static final Logger logger = LoggerFactory.getLogger(TokenMemoryStore.class);
    private final ConcurrentHashMap<String, String> userTokenMap = new ConcurrentHashMap<>();
    
    public void storeToken(String userId, String token) {
        userTokenMap.put(userId, token);
        logger.debug("Stored token for user: {}", userId);
    }
    
    public String getToken(String userId) {
        return userTokenMap.get(userId);
    }
    
    public void clearTokens() {
        userTokenMap.clear();
        logger.info("Cleared all tokens from memory store");
    }
    
    public boolean validateToken(String userId, String token) {
        String storedToken = userTokenMap.get(userId);
        return storedToken != null && storedToken.equals(token);
    }
}
