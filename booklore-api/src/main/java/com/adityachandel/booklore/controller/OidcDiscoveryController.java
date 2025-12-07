package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/oidc")
public class OidcDiscoveryController {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final AppSettingService appSettingService;
    private final RestTemplateBuilder restTemplateBuilder;

    private final AtomicReference<CachedDiscovery> discoveryCache = new AtomicReference<>();

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

        var cached = discoveryCache.get();
        if (cached != null && cached.isFresh()) {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(cached.body());
        }

        String discoveryUrl = providerDetails.getIssuerUri().replaceAll("/+$", "") + "/.well-known/openid-configuration";

        try {
            ResponseEntity<String> response = restTemplate().getForEntity(discoveryUrl, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("OIDC discovery proxy received status {} from {}", response.getStatusCode(), discoveryUrl);
                return ResponseEntity.status(response.getStatusCode())
                    .body(Map.of("error", "Failed to fetch OIDC discovery document"));
            }

            discoveryCache.set(new CachedDiscovery(response.getBody(), Instant.now()));

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
        } catch (Exception ex) {
            log.warn("OIDC discovery proxy failed for {}: {}", discoveryUrl, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                    "error", "OIDC discovery unavailable",
                    "message", ex.getMessage()
                ));
        }
    }

    private RestTemplate restTemplate() {
        return restTemplateBuilder
            .requestFactory(() -> {
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
                factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
                return factory;
            })
            .build();
    }

    private record CachedDiscovery(String body, Instant fetchedAt) {
        boolean isFresh() {
            return fetchedAt != null && Instant.now().isBefore(fetchedAt.plus(CACHE_TTL));
        }
    }
}
