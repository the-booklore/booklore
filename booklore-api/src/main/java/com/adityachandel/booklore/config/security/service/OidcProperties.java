package com.adityachandel.booklore.config.security.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "booklore.security.oidc")
public record OidcProperties(Jwks jwks, Jwt jwt, boolean allowIssuerProtocolMismatch) {

    public OidcProperties {
        if (jwks == null) {
            jwks = new Jwks(
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                1048576,
                Duration.ofHours(6),
                Duration.ofHours(1),
                false,
                "",
                null,
                "BookLore-OIDC-Client/1.0",
                "",
                ""
            );
        }
        if (jwt == null) {
            jwt = new Jwt(Duration.ofSeconds(60));
        }
    }

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
