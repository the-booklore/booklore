package com.adityachandel.booklore.config.security.service;

import com.adityachandel.booklore.service.oidc.OidcDiscoveryService;
import com.adityachandel.booklore.util.OidcUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class OidcDiscoveryServiceWithCustomBaseUrlTest {

    private OidcDiscoveryService oidcDiscoveryService;

    @BeforeEach
    void setUp() {
        OidcProperties.Jwks jwks = new OidcProperties.Jwks(
            java.time.Duration.ofSeconds(5),
            java.time.Duration.ofSeconds(5),
            1048576,
            java.time.Duration.ofMinutes(30),
            java.time.Duration.ofMinutes(10),
            true,
            100,
            "",
            null,
            "BookLore-OIDC-Client/1.0",
            "",
            "",
            ""
        );

        OidcProperties.Jwt jwt = new OidcProperties.Jwt(
            java.time.Duration.ofSeconds(60),
            false,
            10000,
            null
        );

        OidcProperties oidcProperties = new OidcProperties(
            jwks,
            jwt,
            false,  // allowIssuerProtocolMismatch
            true,   // strictIssuerValidation
            false,  // strictAudienceValidation
            false,  // allowUnsafeAlgorithmFallback
            false   // allowInsecureOidcProviders
        );

        oidcDiscoveryService = new OidcDiscoveryService(
            new org.springframework.boot.web.client.RestTemplateBuilder(),
            new com.fasterxml.jackson.databind.ObjectMapper(),
            new org.springframework.core.env.StandardEnvironment(),
            oidcProperties
        );
    }

    @Test
    void resolveDiscoveryUri_ShouldHandleCustomBasePath() {
        String customBaseUrl = "https://example.com/booklore";
        String expectedDiscoveryUri = "https://example.com/booklore/.well-known/openid-configuration";

        String result = OidcUtils.resolveDiscoveryUri(customBaseUrl);

        assertEquals(expectedDiscoveryUri, result,
                "Discovery URI should be correctly resolved for custom base URL");
    }

    @Test
    void normalizeIssuerUri_ShouldHandleCustomBasePath() {
        String issuerWithSlashes = "https://example.com/booklore///";
        String expectedNormalized = "https://example.com/booklore";

        String result = OidcUtils.normalizeIssuerUri(issuerWithSlashes);

        assertEquals(expectedNormalized, result,
                "Issuer URI should be normalized to remove trailing slashes");
    }
}