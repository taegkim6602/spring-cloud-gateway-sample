package com.example.demogateway;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.client.ExchangeFilterFunctions.basicAuthentication;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DemogatewayApplicationTests {

	@LocalServerPort
	int port;
	private WebTestClient client;

	@BeforeEach
	public void setup() {
		client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}



}
