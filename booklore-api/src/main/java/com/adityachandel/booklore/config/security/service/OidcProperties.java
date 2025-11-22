package com.adityachandel.booklore.config.security.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "booklore.security.oidc")
public record OidcProperties(Jwks jwks, Jwt jwt) {

    public record Jwks(
            Duration connectTimeout,
            Duration readTimeout,
            int sizeLimit,
            Duration cacheTtl,
            Duration cacheRefresh,
            boolean rateLimitEnabled,
            String proxyHost,
            Integer proxyPort,
            String userAgent,
            String proxyUser,
            String proxyPassword
    ) {}

    public record Jwt(Duration clockSkew) {}
}
