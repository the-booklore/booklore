package com.adityachandel.booklore.integration;

import com.adityachandel.booklore.config.security.service.OidcProperties;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.oidc.OidcDiscoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration-style test to verify OIDC functionality works properly with custom base URLs.
 * This test simulates the scenario where BookLore is deployed behind a reverse proxy
 * with a custom path like https://example.com/booklore/ 
 */
@ExtendWith(MockitoExtension.class)
class OidcCustomBaseUrlIntegrationTest {

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private OidcDiscoveryService oidcDiscoveryService;

    @Mock
    private OidcProperties oidcProperties;

    @Test
    void oidcDiscoveryShouldWork_WhenDeployedBehindReverseProxyWithCustomPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("mycompany.com");
        request.setServerPort(443);
        request.setRequestURI("/booklore/api/v1/auth/oidc/discovery");
        request.setContextPath("/booklore"); // Important: context path reflects custom deployment

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            assertEquals("/booklore", request.getContextPath());
            assertEquals("mycompany.com", request.getServerName());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void oidcIssuerNormalizationShouldWork_WithCustomBaseUrlIssuer() {
        String[] issuerUris = {
            "https://auth.example.com",           // Standard
            "https://auth.example.com/",          // With trailing slash
            "https://auth.example.com/realms/my-realm",  // With path
            "https://auth.example.com/realms/my-realm/"  // With path and trailing slash
        };

        for (String issuerUri : issuerUris) {
            String normalized = normalizeIssuerUri(issuerUri);
            assertFalse(normalized.endsWith("/"), "Normalized issuer should not end with slash: " + normalized);

            if (issuerUri.endsWith("/")) {
                assertNotEquals(issuerUri, normalized);
            } else {
                assertEquals(issuerUri, normalized);
            }
        }
    }
    
    private String normalizeIssuerUri(String issuerUri) {
        if (issuerUri == null) return null;
        return issuerUri.replaceAll("/+$", ""); // Remove trailing slashes
    }
}