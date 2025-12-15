package com.adityachandel.booklore.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class OidcUtilsTest {

    @Test
    void resolveDiscoveryUri_AppendsConfigurationPath() {
        assertEquals("https://example.com/.well-known/openid-configuration", 
            OidcUtils.resolveDiscoveryUri("https://example.com"));
    }

    @Test
    void resolveDiscoveryUri_HandlesTrailingSlashes() {
        assertEquals("https://example.com/.well-known/openid-configuration", 
            OidcUtils.resolveDiscoveryUri("https://example.com/"));
        assertEquals("https://example.com/.well-known/openid-configuration", 
            OidcUtils.resolveDiscoveryUri("https://example.com///"));
    }

    @Test
    void resolveDiscoveryUri_ReturnsAsIs_IfAlreadyIncludesConfigurationPath() {
        assertEquals("https://example.com/.well-known/openid-configuration", 
            OidcUtils.resolveDiscoveryUri("https://example.com/.well-known/openid-configuration"));
    }

    @Test
    void normalizeIssuerUri_RemovesTrailingSlashes() {
        assertEquals("https://example.com", OidcUtils.normalizeIssuerUri("https://example.com/"));
        assertEquals("https://example.com", OidcUtils.normalizeIssuerUri("https://example.com///"));
        assertEquals("https://example.com", OidcUtils.normalizeIssuerUri("https://example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost/discovery",
        "http://127.0.0.1/discovery",
        "http://192.168.1.1/discovery",
        "http://10.0.0.1/discovery",
        "http://172.16.0.1/discovery",
        "http://0.0.0.0/discovery"
    })
    void validateDiscoveryUri_ThrowsSecurityException_ForInternalAddresses(String uri) {
        assertThrows(SecurityException.class, () -> OidcUtils.validateDiscoveryUri(uri, false));
    }
    
    @Test
    void validateDiscoveryUri_AllowsInternalAddresses_InDevelopment() {
        assertDoesNotThrow(() -> OidcUtils.validateDiscoveryUri("http://localhost/discovery", true));
    }

    @Test
    void validateDiscoveryUri_ThrowsSecurityException_ForHttpInProduction() {
        assertThrows(SecurityException.class, () -> OidcUtils.validateDiscoveryUri("http://example.com/discovery", false));
    }

    @Test
    void validateDiscoveryUri_AllowsHttpsInProduction() {
        assertDoesNotThrow(() -> OidcUtils.validateDiscoveryUri("https://example.com/discovery", false));
    }
}
