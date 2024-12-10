package com.example.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class AuthServiceApplication {
	private static final Logger logger = LoggerFactory.getLogger(AuthServiceApplication.class);
	public static void main(String[] args) {

		logger.info("Starting Authorization Server...");
		SpringApplication.run(AuthServiceApplication.class, args);
		logger.info("Authorization Server is running!");
	}

	@GetMapping("/public")
	public String publicEndpoint() {
		logger.info("Public endpoint accessed");
		return "This is a public endpoint";
	}
}
