package org.booklore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.oidc.OidcDiscoveryService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/oidc")
public class OidcDiscoveryController {

    private final AppSettingService appSettingService;
    private final OidcDiscoveryService oidcDiscoveryService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            var settings = appSettingService.getAppSettings();
            if (settings.isOidcEnabled() && settings.getOidcProviderDetails() != null) {
                oidcDiscoveryService.preWarmCache(settings.getOidcProviderDetails().getIssuerUri());
            }
        } catch (Exception ex) {
            log.debug("Failed to initiate OIDC cache pre-warm: {}", ex.getMessage());
        }
    }

    @GetMapping(value = "/discovery", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> proxyDiscoveryDocument() {
        var settings = appSettingService.getAppSettings();
        if (!settings.isOidcEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "OIDC is disabled"));
        }

        OidcProviderDetails providerDetails = settings.getOidcProviderDetails();
        if (providerDetails == null || providerDetails.getIssuerUri() == null || providerDetails.getIssuerUri().isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "OIDC issuer is not configured"));
        }

        try {
            String document = oidcDiscoveryService.getDiscoveryDocument(providerDetails.getIssuerUri());
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(document);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                    "error", "OIDC discovery unavailable",
                    "message", ex.getMessage()
                ));
        }
    }
}
