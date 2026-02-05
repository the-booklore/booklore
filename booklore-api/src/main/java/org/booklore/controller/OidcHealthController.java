package org.booklore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.DynamicOidcJwtProcessor;
import org.booklore.service.appsettings.AppSettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class OidcHealthController {

    private final AppSettingService appSettingService;
    private final DynamicOidcJwtProcessor dynamicOidcJwtProcessor;

    @GetMapping("/oidc")
    public ResponseEntity<Map<String, Object>> checkOidcHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        
        var appSettings = appSettingService.getAppSettings();
        boolean oidcEnabled = appSettings.isOidcEnabled();
        health.put("enabled", oidcEnabled);
        
        if (!oidcEnabled) {
            health.put("status", "disabled");
            health.put("message", "OIDC authentication is disabled");
            return ResponseEntity.ok(health);
        }
        
        var providerDetails = appSettings.getOidcProviderDetails();
        if (providerDetails == null) {
            health.put("status", "error");
            health.put("message", "OIDC enabled but provider details not configured");
            return ResponseEntity.status(500).body(health);
        }
        
        health.put("issuer", providerDetails.getIssuerUri());
        health.put("clientId", providerDetails.getClientId());
        
        // Check if processor can reach the OIDC provider
        try {
            boolean isHealthy = dynamicOidcJwtProcessor.isHealthy();
            
            if (isHealthy) {
                health.put("status", "healthy");
                health.put("message", "OIDC provider is reachable and configured correctly");
                return ResponseEntity.ok(health);
            } else {
                health.put("status", "unhealthy");
                health.put("message", dynamicOidcJwtProcessor.getHealthStatus());
                return ResponseEntity.status(503).body(health);
            }
        } catch (Exception e) {
            log.error("OIDC health check failed", e);
            health.put("status", "unhealthy");
            health.put("message", "OIDC provider unreachable: " + e.getMessage());
            health.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(503).body(health);
        }
    }
    

    @GetMapping("/oidc/details")
    public ResponseEntity<Map<String, Object>> getOidcDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        
        var appSettings = appSettingService.getAppSettings();
        boolean oidcEnabled = appSettings.isOidcEnabled();
        
        details.put("enabled", oidcEnabled);
        
        if (!oidcEnabled) {
            details.put("message", "OIDC is disabled");
            return ResponseEntity.ok(details);
        }
        
        var providerDetails = appSettings.getOidcProviderDetails();
        if (providerDetails != null) {
            details.put("issuer", providerDetails.getIssuerUri());
            details.put("clientId", providerDetails.getClientId());
            
            if (providerDetails.getClaimMapping() != null) {
                Map<String, String> claims = new LinkedHashMap<>();
                claims.put("username", providerDetails.getClaimMapping().getUsername());
                claims.put("email", providerDetails.getClaimMapping().getEmail());
                claims.put("name", providerDetails.getClaimMapping().getName());
                details.put("claimMapping", claims);
            }
        }
        
        var autoProvision = appSettings.getOidcAutoProvisionDetails();
        if (autoProvision != null) {
            Map<String, Object> provisionConfig = new LinkedHashMap<>();
            provisionConfig.put("enabled", autoProvision.isEnableAutoProvisioning());
            provisionConfig.put("defaultPermissions", autoProvision.getDefaultPermissions());
            provisionConfig.put("defaultLibraryIds", autoProvision.getDefaultLibraryIds());
            details.put("autoProvisioning", provisionConfig);
        }
        
        // Get health status
        String healthStatus = dynamicOidcJwtProcessor.getHealthStatus();
        details.put("healthStatus", healthStatus);
        details.put("isHealthy", dynamicOidcJwtProcessor.isHealthy());
        
        return ResponseEntity.ok(details);
    }
}
