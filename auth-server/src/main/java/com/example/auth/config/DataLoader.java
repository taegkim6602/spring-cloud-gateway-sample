package com.example.auth.config;

import com.example.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataLoader {
    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);
    
    private final UserRepository userRepository;
    private final TokenMemoryStore tokenStore;
    
    @Autowired
    public DataLoader(UserRepository userRepository, TokenMemoryStore tokenStore) {
        this.userRepository = userRepository;
        this.tokenStore = tokenStore;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void loadDataOnStartup() {
        loadUserTokens();
    }
    
    @Scheduled(fixedRate = 3600000) // Reload every hour
    public void reloadData() {
        loadUserTokens();
    }
    
    private void loadUserTokens() {
        logger.info("Loading user tokens into memory...");
        try {
            tokenStore.clearTokens();
            
            userRepository.findAll().forEach(user -> {
                if (user.getUserToken() != null && user.isMember()) {
                    tokenStore.storeToken(user.getUserId(), user.getUserToken());
                    logger.debug("Loaded token for user: {}", user.getUserId());
                }
            });
            
            logger.info("Successfully loaded all user tokens into memory");
        } catch (Exception e) {
            logger.error("Error loading user tokens into memory", e);
            throw new RuntimeException("Failed to load user data into memory", e);
        }
    }
}
