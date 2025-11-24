package com.adityachandel.booklore.service.ephemera;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.ephemera")
public class EphemeraProperties {
    private String baseUrl = "http://10.129.20.50:8286";
    private int timeoutMs = 30000;
}