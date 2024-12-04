package com.example.demogateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class DemogatewayApplication {
	private static final Logger logger = LoggerFactory.getLogger(DemogatewayApplication.class);
	public static void main(String[] args) {

		logger.info("Starting Authorization Server...");
		SpringApplication.run(DemogatewayApplication.class, args);
		logger.info("Authorization Server is running!");
	}

	@GetMapping("/public")
	public String publicEndpoint() {
		logger.info("Public endpoint accessed");
		return "This is a public endpoint";
	}
}
