package com.adityachandel.booklore.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;

@Configuration
public class BeanConfig {

    @Autowired
    private WebSocketMessageBrokerStats webSocketMessageBrokerStats;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @PostConstruct
    public void init() {
        webSocketMessageBrokerStats.setLoggingPeriod(30 * 24 * 60 * 60 * 1000L); // 30 days
    }
}
