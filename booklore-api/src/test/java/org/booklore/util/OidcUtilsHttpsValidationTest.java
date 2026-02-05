package org.booklore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OidcUtilsHttpsValidationTest {

    @Test
    void validateDiscoveryUri_ShouldAllowHttpsInProduction() {
        assertDoesNotThrow(() -> OidcUtils.validateDiscoveryUri("https://auth.example.com/.well-known/openid-configuration", false, false));
    }

    @Test
    void validateDiscoveryUri_ShouldRejectHttpInProductionByDefault() {
        assertThrows(SecurityException.class, () ->
            OidcUtils.validateDiscoveryUri("http://auth.example.com/.well-known/openid-configuration", false, false));
    }

    @Test
    void validateDiscoveryUri_ShouldAllowHttpInProductionWhenExplicitlyAllowed() {
        assertDoesNotThrow(() -> OidcUtils.validateDiscoveryUri("http://auth.example.com/.well-known/openid-configuration", false, true));
    }

    @Test
    void validateDiscoveryUri_ShouldAllowHttpInDevelopment() {
        assertDoesNotThrow(() -> OidcUtils.validateDiscoveryUri("http://auth.example.com/.well-known/openid-configuration", true, false));
    }

    @Test
    void validateDiscoveryUri_ShouldAllowHttpsInDevelopment() {
        assertDoesNotThrow(() -> OidcUtils.validateDiscoveryUri("https://auth.example.com/.well-known/openid-configuration", true, false));
    }

    @Test
    void validateDiscoveryUri_ShouldRejectInternalAddressesInProduction() {
        assertThrows(SecurityException.class, () ->
            OidcUtils.validateDiscoveryUri("https://localhost/.well-known/openid-configuration", false, false));
        
        assertThrows(SecurityException.class, () -> 
            OidcUtils.validateDiscoveryUri("https://127.0.0.1/.well-known/openid-configuration", false, false));
    }

    @Test
    void validateDiscoveryUri_ShouldAllowInternalAddressesInDevelopment() {
        assertDoesNotThrow(() -> OidcUtils.validateDiscoveryUri("http://localhost/.well-known/openid-configuration", true, false));
        assertDoesNotThrow(() -> OidcUtils.validateDiscoveryUri("https://127.0.0.1/.well-known/openid-configuration", true, false));
    }

    @Test
    void validateDiscoveryUri_ShouldAllowHttpWhenInsecureProvidersEnabled() {
        assertDoesNotThrow(() ->
            OidcUtils.validateDiscoveryUri("http://external-provider.com/.well-known/openid-configuration", false, true)
        );
    }

    @Test
    void validateDiscoveryUri_ShouldBlockHttpWhenInsecureProvidersDisabled() {
        SecurityException ex = assertThrows(SecurityException.class, () ->
            OidcUtils.validateDiscoveryUri("http://external-provider.com/.well-known/openid-configuration", false, false)
        );
        assertTrue(ex.getMessage().contains("HTTPS"));
    }

    @Test
    void validateDiscoveryUri_ShouldAllowInternalIpsWhenInsecureProvidersEnabled() {
        assertDoesNotThrow(() ->
            OidcUtils.validateDiscoveryUri("http://localhost/.well-known/openid-configuration", false, true)
        );
        assertDoesNotThrow(() ->
            OidcUtils.validateDiscoveryUri("https://192.168.1.100/.well-known/openid-configuration", false, true)
        );
    }
}