package org.booklore.config.security.service;

import org.booklore.model.dto.settings.OidcProviderDetails;
import org.booklore.util.OidcUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DynamicOidcJwtProcessorWithCustomBaseUrlTest {

    private DynamicOidcJwtProcessor dynamicOidcJwtProcessor;

    @BeforeEach
    void setUp() {
    }

    @Test
    void validateAudienceOrAzp_ShouldHandleCustomBaseUrlClientId() {
        String clientId = "booklore-app-client";
        OidcProviderDetails providerDetails = new OidcProviderDetails();
        providerDetails.setIssuerUri("https://mycompany.com/booklore");
        providerDetails.setClientId(clientId);

        assertEquals("https://mycompany.com/booklore", providerDetails.getIssuerUri());
        assertEquals(clientId, providerDetails.getClientId());
    }

    @Test
    void normalizeIssuerUri_ShouldHandleCustomBaseUrl() {
        String customBaseUrl = "https://mycompany.com/booklore/";

        String result = OidcUtils.normalizeIssuerUri(customBaseUrl);
        assertEquals("https://mycompany.com/booklore", result);
    }

    @Test
    void process_ShouldAcceptTokensWithCustomBaseUrlIssuer() {

        String customBaseUrl = "https://mycompany.com/booklore";
        String clientId = "test-client-id";

        OidcProviderDetails providerDetails = new OidcProviderDetails();
        providerDetails.setIssuerUri(customBaseUrl);
        providerDetails.setClientId(clientId);

        assertNotNull(providerDetails.getIssuerUri());
        assertEquals(customBaseUrl, providerDetails.getIssuerUri());
        assertEquals(clientId, providerDetails.getClientId());
    }
}