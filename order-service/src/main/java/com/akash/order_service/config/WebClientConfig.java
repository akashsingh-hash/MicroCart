package com.akash.order_service.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.web.reactive.function.client.ServletBearerExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClient() {
        return WebClient.builder()
                // Automatically forwards the incoming Bearer JWT token
                // to downstream service calls (e.g., order -> inventory)
                .filter(new ServletBearerExchangeFilterFunction());
    }
}
