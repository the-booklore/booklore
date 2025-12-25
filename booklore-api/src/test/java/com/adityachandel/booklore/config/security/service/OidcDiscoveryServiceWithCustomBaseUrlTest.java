package com.adityachandel.booklore.config.security.service;

import com.adityachandel.booklore.util.OidcUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OidcDiscoveryServiceWithCustomBaseUrlTest {

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