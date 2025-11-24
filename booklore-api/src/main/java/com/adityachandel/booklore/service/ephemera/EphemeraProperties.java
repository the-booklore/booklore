package com.adityachandel.booklore.service.ephemera;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.ephemera")
public class EphemeraProperties {

    private String baseUrl = "http://10.129.20.50:8286";
    private long connectTimeoutMs = 2000;
    private long readTimeoutMs = 30000;
    private List<String> allowedPaths = List.of("/**");
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    private boolean injectUserHeaders = true;
}

