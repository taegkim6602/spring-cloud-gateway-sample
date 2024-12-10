package com.example.auth.controller;

import com.example.auth.service.TokenAuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class TokenAuthController {
    private final TokenAuthenticationService authService;

    @Autowired
    public TokenAuthController(TokenAuthenticationService authService) {
        this.authService = authService;
    }
    
    @PostMapping("/validate")
    public Mono<ResponseEntity<Boolean>> validateToken(
            @RequestParam String userId,
            @RequestParam String token) {
        return authService.validateToken(userId, token)
            .map(ResponseEntity::ok);
    }
}
