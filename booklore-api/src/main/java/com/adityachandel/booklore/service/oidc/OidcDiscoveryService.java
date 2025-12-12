package com.adityachandel.booklore.service.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OidcDiscoveryService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern TRAILING_SLASH_PATTERN = Pattern.compile("/+$");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CachedDiscovery> discoveryCache = new AtomicReference<>();
    private volatile String cachedIssuerUri;

    public OidcDiscoveryService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
                    factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
                    return factory;
                })
                .build();
        this.objectMapper = objectMapper;
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
        String discoveryUrl = TRAILING_SLASH_PATTERN.matcher(issuerUri).replaceAll("") + "/.well-known/openid-configuration";
        
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
            throw new RuntimeException("Failed to fetch OIDC discovery document", ex);
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
            String normalizedExpectedIssuer = TRAILING_SLASH_PATTERN.matcher(expectedIssuerUri).replaceAll("");
            String normalizedActualIssuer = TRAILING_SLASH_PATTERN.matcher(actualIssuer).replaceAll("");
            
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

    private record CachedDiscovery(String body, Instant fetchedAt) {
        boolean isFresh() {
            return fetchedAt != null && Instant.now().isBefore(fetchedAt.plus(CACHE_TTL));
        }
    }
}
