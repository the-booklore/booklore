package com.adityachandel.booklore.config.security.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;
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
        boolean allowUnsafeAlgorithmFallback,
        boolean allowInsecureOidcProviders
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
                100,
                "",
                null,
                "BookLore-OIDC-Client/1.0",
                "",
                "",
                ""
            );
        }
        if (jwt == null) {
            jwt = new Jwt(Duration.ofSeconds(60), false, 10000, null); // Default to null for allowedAlgorithms
        }
    }

    public boolean isAlgorithmFallbackAllowed() {
        return allowUnsafeAlgorithmFallback && !strictAudienceValidation;
    }

    public record Jwks(
            Duration connectTimeout,
            Duration readTimeout,
            int sizeLimit,
            Duration cacheTtl,
            Duration cacheRefresh,
            boolean rateLimitEnabled,
            int rateLimitRequestsPerMinute,
            String proxyHost,
            Integer proxyPort,
            String userAgent,
            String trustedCertificates,
            String proxyUsername,
            String proxyPassword
    ) {
        public Jwks {
            if (cacheRefresh != null && cacheTtl != null && !cacheRefresh.minus(cacheTtl).isNegative()) {
                 throw new IllegalArgumentException("Jwks cacheRefresh must be strictly less than cacheTtl to allow for background refresh. " +
                        "Configured: refresh=" + cacheRefresh + ", ttl=" + cacheTtl);
            }
        }
    }

    public record Jwt(
            Duration clockSkew,
            boolean enableReplayPrevention,
            int replayCacheSize,
            Set<String> allowedAlgorithms // New field for algorithm whitelisting
    ) {
        public Jwt {
            if (replayCacheSize <= 0) {
                throw new IllegalArgumentException("replayCacheSize must be positive, got: " + replayCacheSize);
            }
        }
    }
}
