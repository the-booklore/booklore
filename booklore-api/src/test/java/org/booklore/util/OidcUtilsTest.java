package com.adityachandel.booklore.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class OidcUtilsTest {

    @Test
    void normalizeIssuerUri_ShouldRemoveTrailingSlash() {
        assertEquals("https://auth.example.com", OidcUtils.normalizeIssuerUri("https://auth.example.com/"));
    }
    
    @Test
    void normalizeIssuerUri_ShouldRemoveMultipleTrailingSlashes() {
        assertEquals("https://auth.example.com", OidcUtils.normalizeIssuerUri("https://auth.example.com///"));
    }
    
    @Test
    void normalizeIssuerUri_ShouldNotModifyUriWithoutTrailingSlash() {
        assertEquals("https://auth.example.com", OidcUtils.normalizeIssuerUri("https://auth.example.com"));
    }
    
    @Test
    void normalizeIssuerUri_ShouldHandleNull() {
        assertNull(OidcUtils.normalizeIssuerUri(null));
    }
    
    @Test
    void normalizeIssuerUri_ShouldPreservePath() {
        assertEquals("https://auth.example.com/realms/booklore", 
                     OidcUtils.normalizeIssuerUri("https://auth.example.com/realms/booklore/"));
    }

    @Test
    void resolveDiscoveryUri_ShouldAppendWellKnown() {
        String result = OidcUtils.resolveDiscoveryUri("https://auth.example.com");
        assertEquals("https://auth.example.com/.well-known/openid-configuration", result);
    }
    
    @Test
    void resolveDiscoveryUri_ShouldNotDuplicateWellKnown() {
        String result = OidcUtils.resolveDiscoveryUri("https://auth.example.com/.well-known/openid-configuration");
        assertEquals("https://auth.example.com/.well-known/openid-configuration", result);
    }
    
    @Test
    void resolveDiscoveryUri_ShouldHandleTrailingSlash() {
        String result = OidcUtils.resolveDiscoveryUri("https://auth.example.com/");
        assertEquals("https://auth.example.com/.well-known/openid-configuration", result);
    }
    
    @Test
    void resolveDiscoveryUri_ShouldHandleNull() {
        assertNull(OidcUtils.resolveDiscoveryUri(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/",
            "http://localhost:8080/",
            "http://LOCALHOST/",
            "http://127.0.0.1/",
            "http://127.0.0.2/",  // Full 127.0.0.0/8 range
            "http://0.0.0.0/",
            "http://[::1]/",
            "http://192.168.1.1/",
            "http://192.168.0.1/",
            "http://10.0.0.1/",
            "http://10.255.255.255/",
            "http://172.16.0.1/",
            "http://172.31.255.255/",
            "http://169.254.169.254/",  // AWS metadata
            "http://metadata.google.internal/"  // GCP metadata
    })
    void validateDiscoveryUri_ShouldBlockInternalAddresses(String uri) {
        assertThrows(SecurityException.class, () -> 
            OidcUtils.validateDiscoveryUri(uri, false, false));
    }
    
    @Test
    void validateDiscoveryUri_ShouldAllowInternalInDevelopment() {
        assertDoesNotThrow(() -> 
            OidcUtils.validateDiscoveryUri("http://localhost:8080/", true, false));
    }
    
    @Test
    void validateDiscoveryUri_ShouldAllowInternalWithInsecureFlag() {
        assertDoesNotThrow(() -> 
            OidcUtils.validateDiscoveryUri("http://localhost:8080/", false, true));
    }
    
    @Test
    void validateDiscoveryUri_ShouldBlockHttpInProduction() {
        assertThrows(SecurityException.class, () -> 
            OidcUtils.validateDiscoveryUri("http://auth.example.com/", false, false));
    }
    
    @Test
    void validateDiscoveryUri_ShouldAllowHttps() {
        assertDoesNotThrow(() -> 
            OidcUtils.validateDiscoveryUri("https://auth.example.com/", false, false));
    }
    
    @Test
    void validateDiscoveryUri_ShouldHandleNull() {
        assertDoesNotThrow(() -> 
            OidcUtils.validateDiscoveryUri(null, false, false));
    }
    
    @Test
    void validateDiscoveryUri_ShouldBlockInvalidUri() {
        assertThrows(SecurityException.class, () -> 
            OidcUtils.validateDiscoveryUri("not-a-valid-uri", false, false));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost.localdomain/",
            "http://myservice.localhost/",
            "http://myservice.local/"
    })
    void validateDiscoveryUri_ShouldBlockLocalhostVariants(String uri) {
        assertThrows(SecurityException.class, () -> 
            OidcUtils.validateDiscoveryUri(uri, false, false));
    }

    @Test
    void resolveDiscoveryUri_ShouldHandleAuthentikIssuer() {
        // Authentik typically uses: https://auth.example.com/application/o/booklore/
        String result = OidcUtils.resolveDiscoveryUri("https://auth.example.com/application/o/booklore/");
        assertEquals("https://auth.example.com/application/o/booklore/.well-known/openid-configuration", result);
    }
    
    @Test
    void resolveDiscoveryUri_ShouldHandleKeycloakIssuer() {
        // Keycloak uses: https://auth.example.com/realms/booklore
        String result = OidcUtils.resolveDiscoveryUri("https://auth.example.com/realms/booklore");
        assertEquals("https://auth.example.com/realms/booklore/.well-known/openid-configuration", result);
    }
    
    @Test
    void resolveDiscoveryUri_ShouldHandleAutheliaIssuer() {
        // Authelia uses: https://auth.example.com
        String result = OidcUtils.resolveDiscoveryUri("https://auth.example.com");
        assertEquals("https://auth.example.com/.well-known/openid-configuration", result);
    }
    
    @Test
    void resolveDiscoveryUri_ShouldHandlePocketIDIssuer() {
        // PocketID uses: https://pocketid.example.com
        String result = OidcUtils.resolveDiscoveryUri("https://pocketid.example.com");
        assertEquals("https://pocketid.example.com/.well-known/openid-configuration", result);
    }

    @Test
    void validateDiscoveryUri_ShouldBlockPrivate172Range() {
        // 172.16.0.0 to 172.31.255.255 is private, but 172.32.x.x is not
        assertThrows(SecurityException.class, () -> 
            OidcUtils.validateDiscoveryUri("http://172.20.0.1/", false, false));
        
        // Note: 172.32.x.x would pass the internal check but fail HTTPS check
        assertThrows(SecurityException.class, () -> 
            OidcUtils.validateDiscoveryUri("http://172.32.0.1/", false, false));
    }
    
    @Test
    void validateDiscoveryUri_ShouldAllowPublic172Addresses() {
        // 172.32.0.1 is technically public, should pass with HTTPS
        assertDoesNotThrow(() -> 
            OidcUtils.validateDiscoveryUri("https://172.32.0.1/", false, false));
    }
    
    @Test
    void normalizeIssuerUri_ShouldHandleEmptyString() {
        assertEquals("", OidcUtils.normalizeIssuerUri(""));
    }
    
    @Test
    void normalizeIssuerUri_ShouldHandleOnlySlashes() {
        assertEquals("", OidcUtils.normalizeIssuerUri("///"));
    }
}
