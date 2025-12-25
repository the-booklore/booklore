package com.adityachandel.booklore.config;

import com.adityachandel.booklore.config.properties.WebSocketProperties;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;

import java.time.Duration;

@Configuration
@AllArgsConstructor
public class BeanConfig {

    private final WebSocketProperties webSocketProperties;

    @Autowired(required = false)
    private WebSocketMessageBrokerStats webSocketMessageBrokerStats;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofSeconds(15))
                .build();
    }

    @PostConstruct
    public void init() {
        if (webSocketMessageBrokerStats != null) {
            // Use configurable logging period in days
            long loggingPeriodMs = webSocketProperties.getLoggingPeriodDays() * 24L * 60L * 60L * 1000L; // days to milliseconds
            webSocketMessageBrokerStats.setLoggingPeriod(loggingPeriodMs);
        }
    }
}
