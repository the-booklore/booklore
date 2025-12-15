package com.adityachandel.booklore.service.oidc;

import com.adityachandel.booklore.util.OidcUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class OidcDiscoveryService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final AtomicReference<CachedDiscovery> discoveryCache = new AtomicReference<>();
    private volatile String cachedIssuerUri;

    public OidcDiscoveryService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper, Environment environment) {
        this.restTemplate = restTemplateBuilder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
                    factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
                    return factory;
                })
                .build();
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    public void invalidateCache() {
        log.info("OIDC discovery cache invalidated");
        discoveryCache.set(null);
        cachedIssuerUri = null;
    }

    @Async
    public void preWarmCache(String issuerUri) {
        if (issuerUri == null || issuerUri.isBlank()) {
            return;
        }
        try {
            fetchDiscoveryDocument(issuerUri);
            log.info("OIDC discovery cache pre-warmed successfully");
        } catch (Exception ex) {
            log.debug("Failed to pre-warm OIDC discovery cache: {}", ex.getMessage());
        }
    }

    public String getDiscoveryDocument(String issuerUri) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalArgumentException("Issuer URI cannot be null or empty");
        }

        // Check if issuer changed
        if (cachedIssuerUri != null && !cachedIssuerUri.equals(issuerUri)) {
            log.info("OIDC issuer changed from '{}' to '{}', invalidating discovery cache", cachedIssuerUri, issuerUri);
            invalidateCache();
        }

        CachedDiscovery cached = discoveryCache.get();
        if (cached != null && cached.isFresh()) {
            return cached.body();
        }

        return fetchDiscoveryDocument(issuerUri);
    }

    private String fetchDiscoveryDocument(String issuerUri) {
        String discoveryUrl = OidcUtils.resolveDiscoveryUri(issuerUri);
        
        // Validate URI security
        OidcUtils.validateDiscoveryUri(discoveryUrl, isDevelopmentEnvironment());
        
        try {
            log.info("Fetching OIDC discovery document from: {}", discoveryUrl);
            ResponseEntity<String> response = restTemplate.getForEntity(discoveryUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String rewrittenBody = rewriteIssuerInDiscoveryDocument(response.getBody(), issuerUri);
                discoveryCache.set(new CachedDiscovery(rewrittenBody, Instant.now()));
                cachedIssuerUri = issuerUri;
                return rewrittenBody;
            } else {
                throw new RuntimeException("Received status " + response.getStatusCode() + " from " + discoveryUrl);
            }
        } catch (Exception ex) {
            log.warn("OIDC discovery failed for {}: {}", discoveryUrl, ex.getMessage());
            throw new RuntimeException("Failed to fetch OIDC discovery document: " + ex.getMessage(), ex);
        }
    }

    private String rewriteIssuerInDiscoveryDocument(String discoveryDocumentJson, String expectedIssuerUri) {
        try {
            JsonNode rootNode = objectMapper.readTree(discoveryDocumentJson);
            
            if (!(rootNode instanceof ObjectNode mutableRoot)) {
                log.warn("Discovery document root is not a JSON object, cannot rewrite issuer");
                return discoveryDocumentJson;
            }
            
            if (!rootNode.has("issuer")) {
                log.warn("Discovery document does not contain an issuer field");
                return discoveryDocumentJson;
            }
            
            String actualIssuer = rootNode.get("issuer").asText();
            String normalizedExpectedIssuer = OidcUtils.normalizeIssuerUri(expectedIssuerUri);
            String normalizedActualIssuer = OidcUtils.normalizeIssuerUri(actualIssuer);
            
            if (!normalizedActualIssuer.equals(normalizedExpectedIssuer)) {
                log.debug("Rewriting issuer in discovery document from '{}' to '{}' for frontend compatibility. " +
                         "Backend JWT validation will use booklore.security.oidc.strict-issuer-validation setting.",
                         actualIssuer, normalizedExpectedIssuer);

                mutableRoot.put("issuer", normalizedExpectedIssuer);
                
                return objectMapper.writeValueAsString(mutableRoot);
            }
            
            return discoveryDocumentJson;
        } catch (Exception ex) {
            log.warn("Failed to rewrite issuer in discovery document: {}. Returning original document. " +
                    "This may cause frontend validation errors if provider returns internal URLs.",
                    ex.getMessage());
            return discoveryDocumentJson;
        }
    }

    private boolean isDevelopmentEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length > 0 && 
               (Arrays.asList(activeProfiles).contains("dev") || 
                Arrays.asList(activeProfiles).contains("development") || 
                Arrays.asList(activeProfiles).contains("local"));
    }

    private record CachedDiscovery(String body, Instant fetchedAt) {
        boolean isFresh() {
            return fetchedAt != null && Instant.now().isBefore(fetchedAt.plus(CACHE_TTL));
        }
    }
}
