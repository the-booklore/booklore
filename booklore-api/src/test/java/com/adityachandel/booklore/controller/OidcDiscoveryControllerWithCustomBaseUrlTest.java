package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.oidc.OidcDiscoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OidcDiscoveryControllerWithCustomBaseUrlTest {

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private OidcDiscoveryService oidcDiscoveryService;

    @InjectMocks
    private OidcDiscoveryController oidcDiscoveryController;

    @Test
    void proxyDiscoveryDocument_ShouldReturnDiscoveryDoc_WhenOidcEnabledWithCustomBaseUrl() {
        String customBaseUrl = "https://mycompany.com/booklore";
        
        OidcProviderDetails providerDetails = new OidcProviderDetails();
        providerDetails.setIssuerUri(customBaseUrl);
        
        AppSettings settings = new AppSettings();
        settings.setOidcEnabled(true);
        settings.setOidcProviderDetails(providerDetails);

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcDiscoveryService.getDiscoveryDocument(customBaseUrl))
            .thenReturn("{\"issuer\":\"" + customBaseUrl + "\",\"authorization_endpoint\":\"" + customBaseUrl + "/auth\"}");

        ResponseEntity<?> response = oidcDiscoveryController.proxyDiscoveryDocument();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().toString().contains(customBaseUrl));
        verify(oidcDiscoveryService).getDiscoveryDocument(customBaseUrl);
    }

    @Test
    void proxyDiscoveryDocument_ShouldReturn404_WhenOidcDisabled() {
        AppSettings settings = new AppSettings();
        settings.setOidcEnabled(false);

        when(appSettingService.getAppSettings()).thenReturn(settings);
        
        ResponseEntity<?> response = oidcDiscoveryController.proxyDiscoveryDocument();
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("OIDC is disabled"));
    }

    @Test
    void proxyDiscoveryDocument_ShouldReturn404_WhenIssuerNotConfigured() {
        AppSettings settings = new AppSettings();
        settings.setOidcEnabled(true);
        settings.setOidcProviderDetails(null); // No provider details

        when(appSettingService.getAppSettings()).thenReturn(settings);
        
        ResponseEntity<?> response = oidcDiscoveryController.proxyDiscoveryDocument();
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("OIDC issuer is not configured"));
    }

    @Test
    void proxyDiscoveryDocument_ShouldReturn404_WhenIssuerUriEmpty() {
        OidcProviderDetails providerDetails = new OidcProviderDetails();
        providerDetails.setIssuerUri(null); // Null issuer URI

        AppSettings settings = new AppSettings();
        settings.setOidcEnabled(true);
        settings.setOidcProviderDetails(providerDetails);

        when(appSettingService.getAppSettings()).thenReturn(settings);
        
        ResponseEntity<?> response = oidcDiscoveryController.proxyDiscoveryDocument();
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("OIDC issuer is not configured"));
    }

    @Test
    void proxyDiscoveryDocument_ShouldReturnBadGateway_WhenDiscoveryFails() {
        String customBaseUrl = "https://mycompany.com/booklore";
        
        OidcProviderDetails providerDetails = new OidcProviderDetails();
        providerDetails.setIssuerUri(customBaseUrl);

        AppSettings settings = new AppSettings();
        settings.setOidcEnabled(true);
        settings.setOidcProviderDetails(providerDetails);

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcDiscoveryService.getDiscoveryDocument(customBaseUrl))
            .thenThrow(new RuntimeException("Discovery failed"));
        
        ResponseEntity<?> response = oidcDiscoveryController.proxyDiscoveryDocument();
        
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("OIDC discovery unavailable"));
    }
}