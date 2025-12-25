package com.adityachandel.booklore.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {
    
    private String[] allowedOrigins = {"http://localhost:3000", "http://localhost:8080", "https://localhost:3000", "https://localhost:8080", "https://localhost:4200", "http://localhost:4200"};
}