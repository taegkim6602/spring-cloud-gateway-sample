package com.example.auth.service;

import com.example.auth.config.TokenMemoryStore;
import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class TokenAuthenticationService {
    private final TokenMemoryStore tokenStore;
    private final UserRepository userRepository;
    
    @Autowired
    public TokenAuthenticationService(TokenMemoryStore tokenStore, UserRepository userRepository) {
        this.tokenStore = tokenStore;
        this.userRepository = userRepository;
    }
    
    public Mono<Boolean> validateToken(String userId, String token) {
        return Mono.fromCallable(() -> {
            if (!tokenStore.validateToken(userId, token)) {
                return false;
            }
            
            User user = userRepository.findByUserId(userId);
            if (user == null || !user.isMember()) {
                return false;
            }
            
            // Check expiration
            LocalDate expiration = LocalDate.parse(user.getExpirationDate(), 
                DateTimeFormatter.ofPattern("yyyyMMdd"));
            if (LocalDate.now().isAfter(expiration)) {
                return false;
            }
            
            // Check daily traffic limit
            int usage = Integer.parseInt(user.getDailyTrafficUsage());
            int limit = Integer.parseInt(user.getDailyTrafficLimit());
            return usage < limit;
        });
    }
}
