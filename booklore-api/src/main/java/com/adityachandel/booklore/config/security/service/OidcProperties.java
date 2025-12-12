package com.adityachandel.booklore.config.security.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OIDC security configuration properties.
 * 
 * Security recommendations for production:
 * - strictIssuerValidation: true (default)
 * - strictAudienceValidation: true (default)
 * - allowUnsafeAlgorithmFallback: false (default) - NEVER enable in production
 * - allowIssuerProtocolMismatch: false (default) - only for dev environments
 */
@ConfigurationProperties(prefix = "booklore.security.oidc")
public record OidcProperties(
        Jwks jwks, 
        Jwt jwt, 
        boolean allowIssuerProtocolMismatch, 
        boolean strictIssuerValidation, 
        boolean strictAudienceValidation, 
        boolean allowUnsafeAlgorithmFallback
) {

    public OidcProperties {
        if (jwks == null) {
            jwks = new Jwks(
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                1048576,
                Duration.ofMinutes(30),
                Duration.ofMinutes(10),
                true,
                "",
                null,
                "BookLore-OIDC-Client/1.0",
                "",
                ""
            );
        }
        if (jwt == null) {
            jwt = new Jwt(Duration.ofSeconds(60), false, 10000);
        }
    }
    
    /**
     * Returns true only if algorithm fallback is explicitly enabled AND 
     * strict audience validation is disabled (indicating non-production use).
     * This prevents algorithm confusion attacks in production.
     */
    public boolean allowUnsafeAlgorithmFallback() {
        // Extra safety: only allow if explicitly enabled
        // In the future, consider removing this entirely
        return allowUnsafeAlgorithmFallback && !strictAudienceValidation;
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
