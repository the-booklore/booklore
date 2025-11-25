package com.adityachandel.booklore.config.security.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "booklore.security.oidc")
public record OidcProperties(Jwks jwks, Jwt jwt, boolean allowIssuerProtocolMismatch, boolean strictIssuerValidation, boolean strictAudienceValidation) {

    public OidcProperties {
        if (jwks == null) {
            jwks = new Jwks(
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                1048576,
                Duration.ofMinutes(30), // Reduced from 6h to 30m for faster key revocation
                Duration.ofMinutes(10), // Reduced from 1h to 10m
                true, // Enabled by default (was false)
                "",
                null,
                "BookLore-OIDC-Client/1.0",
                "",
                ""
            );
        }
        if (jwt == null) {
            jwt = new Jwt(Duration.ofSeconds(60), false, 10000); // Disable replay prevention by default for tests
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

    public record Jwt(
            Duration clockSkew, 
            boolean enableReplayPrevention,
            int replayCacheSize
    ) {
        public Jwt {
            if (replayCacheSize <= 0) {
                throw new IllegalArgumentException("replayCacheSize must be positive, got: " + replayCacheSize);
            }
        }
    }
}
