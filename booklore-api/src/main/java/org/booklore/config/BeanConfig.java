package org.booklore.config;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.booklore.config.properties.WebSocketProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@AllArgsConstructor
public class BeanConfig {

    private final WebSocketProperties webSocketProperties;

    @Autowired(required = false)
    private WebSocketMessageBrokerStats webSocketMessageBrokerStats;

    @Bean
    public RestTemplate restTemplate(HttpClient httpClient) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return new RestTemplate(factory);
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
