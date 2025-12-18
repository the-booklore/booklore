package com.adityachandel.booklore.config.security.service;

import com.adityachandel.booklore.util.OidcUtils;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
@EnableConfigurationProperties(OidcProperties.class)
@RequiredArgsConstructor
public class DynamicOidcJwtProcessor {
    private static final Set<JWSAlgorithm> KNOWN_JWS_ALGORITHMS = Set.of(
            JWSAlgorithm.RS256,
            JWSAlgorithm.RS384,
            JWSAlgorithm.RS512,
            JWSAlgorithm.PS256,
            JWSAlgorithm.PS384,
            JWSAlgorithm.PS512,
            JWSAlgorithm.ES256,
            JWSAlgorithm.ES256K,
            JWSAlgorithm.ES384,
            JWSAlgorithm.ES512,
            JWSAlgorithm.EdDSA,
            JWSAlgorithm.HS256,
            JWSAlgorithm.HS384,
            JWSAlgorithm.HS512
    );

    private final AppSettingService appSettingService;
    private final OidcProperties oidcProperties;
    private final Environment environment;
    
    private volatile boolean healthCheckCompleted = false;

    private volatile ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private volatile String currentIssuerUri;
    private volatile String currentClientId;

    // Cache for JWT IDs to prevent token replay attacks
    private volatile Cache<String, Boolean> usedTokenCache;
    private final Object cacheInitLock = new Object();

    // Read-write lock for thread-safe configuration changes and concurrent token processing
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public ConfigurableJWTProcessor<SecurityContext> getProcessor() throws Exception {
        var appSettings = appSettingService.getAppSettings();
        OidcProviderDetails providerDetails = appSettings.getOidcProviderDetails();

        if (providerDetails == null) {
            throw new IllegalStateException("OIDC provider details are not configured in app settings");
        }

        String issuerUri = providerDetails.getIssuerUri();
        String normalizedIssuerUri = normalizeIssuerUri(issuerUri);
        log.debug("Issuer from database: '{}', Normalized: '{}'", issuerUri, normalizedIssuerUri);
        if (!Objects.equals(issuerUri, normalizedIssuerUri)) {
            log.debug("Normalized issuer URI from '{}' to '{}'", issuerUri, normalizedIssuerUri);
        }
        String clientId = providerDetails.getClientId();

        ConfigurableJWTProcessor<SecurityContext> localRef = jwtProcessor;
        
        if (localRef == null || !Objects.equals(normalizedIssuerUri, currentIssuerUri) || !Objects.equals(clientId, currentClientId)) {
            
            log.debug("OIDC configuration change detected. Fetching new configuration...");
            
            // 1. Perform heavy network operation OUTSIDE the lock
            // This prevents blocking all auth threads if the IdP is slow
            ConfigurableJWTProcessor<SecurityContext> newProcessor = buildProcessor(providerDetails, normalizedIssuerUri);

            // 2. Acquire write lock only to swap the reference
            writeLock.lock();
            try {
                // 3. Double-check: Did another thread update it while we were building?
                if (this.jwtProcessor == null || !Objects.equals(normalizedIssuerUri, currentIssuerUri) || !Objects.equals(clientId, currentClientId)) {
                    this.jwtProcessor = newProcessor;
                    this.currentIssuerUri = normalizedIssuerUri;
                    this.currentClientId = clientId;
                    localRef = this.jwtProcessor;
                    log.info("OIDC Processor updated successfully.");
                } else {
                    // Another thread beat us to it. Use the already updated one.
                    localRef = this.jwtProcessor;
                    log.debug("OIDC configuration already updated by another thread. Discarding redundant build.");
                }
            } finally {
                writeLock.unlock();
            }
        }
        return localRef;
    }

    private void logTokenMetadata(String token) throws BadJWTException {
        try {
            var jwt = com.nimbusds.jwt.SignedJWT.parse(token);
            var claims = jwt.getJWTClaimsSet();
            var header = jwt.getHeader();

            var now = Instant.now();
            long secondsLeft = claims.getExpirationTime() != null
                ? Duration.between(now, Instant.ofEpochMilli(claims.getExpirationTime().getTime())).getSeconds()
                : 0;

            String maskedSub = claims.getSubject() != null ? mask(claims.getSubject()) : "null";
            String maskedAud = claims.getAudience() != null && !claims.getAudience().isEmpty() ? mask(String.join(",", claims.getAudience())) : "null";
            String maskedAzp = claims.getClaim("azp") instanceof String azpString ? mask(azpString) : "null";

            if (log.isDebugEnabled()) {
                log.debug("Processing JWT - Header: [alg={}, kid={}]; Payload: [iss={}, sub={}, aud={}, azp={}, exp_in={}s]",
                        header.getAlgorithm(),
                        header.getKeyID(),
                        claims.getIssuer(),
                        maskedSub,
                        maskedAud,
                        maskedAzp,
                        secondsLeft
                );
            }

            if (secondsLeft > 0 && secondsLeft < 300) {
                log.info("Token nearing expiry ({}s remaining). Frontend should refresh soon.", secondsLeft);
            }

            if (secondsLeft < 0) {
                log.warn("Token received is already expired by {} seconds. Check server clock sync.", Math.abs(secondsLeft));
            }
            
            var algorithm = header.getAlgorithm();
            if (isHmacAlgorithm(algorithm)) {
                log.warn("Token uses HMAC algorithm '{}'. This is incompatible with JWKS public key validation.", algorithm);
                log.warn("Configure your IdP to use RS256 algorithm.");
                
                if (!oidcProperties.allowUnsafeAlgorithmFallback()) {
                    throw new BadJWTException("HMAC algorithm '" + algorithm + "' is not allowed. Configure IdP to use RS256.");
                }
            } else if (log.isDebugEnabled()) {
                if (isRsaAlgorithm(algorithm) || algorithm.equals(JWSAlgorithm.PS256) || 
                           algorithm.equals(JWSAlgorithm.PS384) || algorithm.equals(JWSAlgorithm.PS512)) {
                    log.debug("Token uses compatible asymmetric algorithm: {}", algorithm);
                } else if (isEcAlgorithm(algorithm)) {
                    log.debug("Token uses EC algorithm: {}. Verify JWKS contains EC keys.", algorithm);
                }
            }

        } catch (BadJWTException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Received token that could not be parsed for logging: {}", e.getMessage());
        }
    }

    public JWTClaimsSet process(String token) throws Exception {
        logTokenMetadata(token);

        ConfigurableJWTProcessor<SecurityContext> processor = getProcessor();
        readLock.lock();
        try {
            JWTClaimsSet claimsSet;
            try {
                claimsSet = processor.process(token, null);
            } catch (BadJOSEException e) {
                if (e.getMessage() != null && e.getMessage().contains("Another algorithm expected")) {
                    log.warn("ALGORITHM MISMATCH DETECTED: Token algorithm doesn't match JWKS key types. Attempting dynamic fallback...");
                    claimsSet = handleAlgorithmMismatch(token, e);
                } else {
                    throw e;
                }
            }

            if (oidcProperties.jwt().enableReplayPrevention()) {
                String jti = claimsSet.getJWTID();
                if (jti != null && !jti.isEmpty()) {
                    Cache<String, Boolean> localCache = usedTokenCache;
                    if (localCache == null) {
                        synchronized (cacheInitLock) {
                            localCache = usedTokenCache;
                            if (localCache == null) {
                                usedTokenCache = Caffeine.newBuilder()
                                    .expireAfterWrite(Duration.ofHours(2)) // Tokens typically expire within 1h
                                    .maximumSize(oidcProperties.jwt().replayCacheSize())
                                    .build();
                                localCache = usedTokenCache;
                                log.info("Initialized JWT replay prevention cache (size: {}, TTL: 2h)",
                                    oidcProperties.jwt().replayCacheSize());
                            }
                        }
                    }

                    Boolean previous = localCache.asMap().putIfAbsent(jti, Boolean.TRUE);
                    if (previous == null) {
                        log.debug("Token JTI '{}' cached to prevent replay", jti);
                    } else {
                        log.warn("SECURITY: Token replay detected for JTI '{}'", jti);
                        throw new BadJWTException("Token replay detected (JTI already used)");
                    }
                } else {
                    log.error("SECURITY: Token does not contain JTI claim. Replay prevention is enabled but JTI is required for security.");
                    throw new BadJWTException("Token missing JTI claim (required for replay prevention)");
                }
            }

            return claimsSet;
        } catch (BadJWTException e) {
            log.error("JWT Verification Failed: {}", e.getMessage());

            String msg = e.getMessage();
            if (msg.contains("Issuer")) {
                log.error("Issuer Mismatch Hint: Configured='{}' vs Token Claim. Check trailing slashes or http/https.", currentIssuerUri);
            } else if (msg.contains("Audience")) {
                log.error("Audience Mismatch Hint: Configured ClientID='{}'. Ensure your OIDC provider maps this Client ID to the 'aud' claim.", currentClientId);
            } else if (msg.contains("Expired JWT")) {
                log.error("Token Expired Hint: The token is no longer valid. If this happens immediately after login, check if the server time is synchronized with the OIDC provider.");
            } else if (msg.contains("nbf") || msg.contains("Not Before")) {
                log.error("Token Not Yet Valid Hint: The token's 'nbf' claim is in the future. Check if the server time is synchronized with the OIDC provider (possible clock skew).");
            }
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing JWT: {}", e.getMessage(), e);
            throw e;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Handles JWT algorithm mismatch by attempting to process tokens with algorithms not in JWKS.
     * This is a fallback for providers (like Authentik, Authelia) that may sign with HS256 but advertise RSA keys.
     */
    private JWTClaimsSet handleAlgorithmMismatch(String token, BadJOSEException originalException) throws Exception {
        if (!oidcProperties.allowUnsafeAlgorithmFallback()) {
            throw originalException;
        }
        try {
            var jwt = com.nimbusds.jwt.SignedJWT.parse(token);
            var header = jwt.getHeader();
            var algorithm = header.getAlgorithm();
            
            log.warn("Token uses algorithm '{}' which is not supported by current JWKS keys. " +
                    "This typically indicates an IdP misconfiguration.", algorithm);
            
            if (isHmacAlgorithm(algorithm)) {
                log.warn("Token signed with HMAC algorithm '{}' but JWKS contains asymmetric keys. " +
                        "HMAC algorithms require a shared secret and cannot be validated via JWKS. " +
                        "Configure your OIDC provider to use RS256 or another asymmetric algorithm.", algorithm);
            } else if (isEcAlgorithm(algorithm)) {
                log.warn("Token uses EC algorithm '{}' but JWKS may only contain RSA keys. " +
                        "Verify your OIDC provider's JWKS includes EC keys.", algorithm);
            } else {
                log.warn("Token uses unexpected algorithm '{}'. Supported algorithms: [RS256, RS384, RS512, PS256, PS384, PS512, ES256, ES384, ES512]", 
                        algorithm);
            }
            
            log.info("Update IdP configuration at issuer='{}' to sign tokens with RS256 algorithm " +
                    "matching the public keys in JWKS endpoint.", currentIssuerUri);
            log.info("See docs/OIDC-PROVIDER-CONFIG.md for step-by-step configuration guides (Authentik, Authelia, Keycloak).");
            
        } catch (Exception parseEx) {
            log.error("Failed to parse token header for algorithm inspection: {}", parseEx.getMessage());
        }
        
        throw new BadJOSEException("JWT algorithm mismatch: Token algorithm does not match available JWKS keys. " +
                "This is typically caused by IdP misconfiguration (e.g., signing with HS256 but advertising RSA keys). " +
                "Please reconfigure your OIDC provider to use RS256 signing. See docs/OIDC-PROVIDER-CONFIG.md for instructions.", originalException);
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor(OidcProviderDetails providerDetails, String normalizedIssuerUri) throws Exception {
        if (providerDetails == null) {
            throw new IllegalStateException("OIDC provider details are not configured in app settings.");
        }

        if (normalizedIssuerUri == null || normalizedIssuerUri.isEmpty()) {
            throw new IllegalStateException("OIDC issuer URI is not configured in app settings.");
        }

        String clientId = providerDetails.getClientId();
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalStateException("OIDC client ID is not configured in app settings for issuer: " + normalizedIssuerUri);
        }

        String discoveryUri = resolveDiscoveryUri(providerDetails, normalizedIssuerUri);
        
        OidcUtils.validateDiscoveryUri(discoveryUri, isDevelopmentEnvironment());
        
        log.info("Fetching OIDC discovery document from {}", discoveryUri);

        var resourceRetriever = createResourceRetriever();
        DiscoveryConfiguration discoveryConfiguration = fetchDiscoveryConfiguration(discoveryUri, resourceRetriever);

        if (discoveryConfiguration.issuer() != null) {
            String discoveredIssuer = normalizeIssuerUri(discoveryConfiguration.issuer());
            if (discoveredIssuer != null && !discoveredIssuer.isEmpty() && !Objects.equals(discoveredIssuer, normalizedIssuerUri)) {
                if (oidcProperties.strictIssuerValidation()) {
                    throw new IllegalStateException(
                        "SECURITY: Issuer mismatch detected. Expected: " + normalizedIssuerUri + 
                        ", Got: " + discoveredIssuer + 
                        ". Set booklore.security.oidc.strict-issuer-validation=false to allow (not recommended in production).");
                }
                log.warn("SECURITY WARNING: Issuer mismatch between configuration ({}) and discovery document ({}). " +
                         "Proceeding with configured issuer due to strict validation being disabled. " +
                         "This could indicate a misconfiguration or potential security issue.",
                         normalizedIssuerUri, discoveredIssuer);
            }
        }

        URI jwksUri = discoveryConfiguration.jwksUri();

        // 1. Start with Advertised Algorithms
        // Use LinkedHashSet to maintain order and allow modification
        Set<JWSAlgorithm> supportedAlgorithms = new LinkedHashSet<>(discoveryConfiguration.supportedAlgorithms().isEmpty()
            ? Set.of(JWSAlgorithm.RS256)
            : discoveryConfiguration.supportedAlgorithms());

        // 2. Filter by Configured Allowed Algorithms (Fail Fast)
        // We do this BEFORE fetching JWKS to avoid unnecessary network calls if configuration denies all advertised algorithms
        if (oidcProperties.jwt().allowedAlgorithms() != null && !oidcProperties.jwt().allowedAlgorithms().isEmpty()) {
            Set<JWSAlgorithm> configuredAllowed = oidcProperties.jwt().allowedAlgorithms().stream()
                .map(JWSAlgorithm::parse)
                .collect(Collectors.toSet());
            
            Set<JWSAlgorithm> originalSupported = new LinkedHashSet<>(supportedAlgorithms);
            supportedAlgorithms.retainAll(configuredAllowed);
            
            if (!originalSupported.equals(supportedAlgorithms)) {
                log.info("Filtered advertised algorithms from {} to {} based on 'allowed-algorithms' config.", 
                        originalSupported, supportedAlgorithms);
            }
            
            if (supportedAlgorithms.isEmpty()) {
                throw new IllegalStateException("No supported JWS algorithms remaining after applying 'booklore.security.oidc.jwt.allowed-algorithms' filter. " +
                                                "Advertised: " + discoveryConfiguration.supportedAlgorithms() + 
                                                ", Allowed: " + oidcProperties.jwt().allowedAlgorithms());
            }
        }

        // 3. Fetch JWKS
        Resource jwksResource;
        try {
            jwksResource = resourceRetriever.retrieveResource(jwksUri.toURL());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch JWKS from " + jwksUri + ": " + e.getMessage(), e);
        }
        var jwkSet = JWKSet.parse(jwksResource.getContent());

        // 4. Filter by Actual Keys (Safety check to avoid mismatch exceptions later)
        supportedAlgorithms = filterAlgorithmsByJwks(jwkSet, jwksUri, supportedAlgorithms);

        log.debug("Configuring JWKS retrieval from {} with timeouts: connect={}ms, read={}ms, sizeLimit={}bytes, algorithms={}",
                jwksUri, oidcProperties.jwks().connectTimeout().toMillis(), oidcProperties.jwks().readTimeout().toMillis(), oidcProperties.jwks().sizeLimit(), supportedAlgorithms);

        var jwkSourceBuilder = JWKSourceBuilder
                .create(jwksUri.toURL(), resourceRetriever);

        if (oidcProperties.jwks().rateLimitEnabled()) {
            jwkSourceBuilder.rateLimited(true);
            log.debug("JWKS rate limiting enabled for security (recommended for production)");
        } else {
            jwkSourceBuilder.rateLimited(false);
            log.warn("JWKS rate limiting disabled. This reduces protection against JWKS endpoint flooding attacks. " +
                    "Only disable for high-trust internal networks with alternative DDoS protection.");
        }

        jwkSourceBuilder.cache(oidcProperties.jwks().cacheTtl().toMillis(), oidcProperties.jwks().cacheRefresh().toMillis());

        var jwkSource = jwkSourceBuilder.build();

        var keySelector = new JWSVerificationKeySelector<>(supportedAlgorithms, jwkSource);
        var processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(keySelector);

        long clockSkewSeconds = oidcProperties.jwt().clockSkew().toSeconds();
        log.debug("JWT clock skew tolerance set to {}s for handling clock drift between services", clockSkewSeconds);

        log.info("Setting up JWT verifier with expected issuer: '{}'", normalizedIssuerUri);
        
        var expectedClaims = new JWTClaimsSet.Builder()
                .build();

        var claimsVerifier = new DefaultJWTClaimsVerifier<>(expectedClaims, Set.of("sub", "exp", "iss")) {
            @Override
            public void verify(JWTClaimsSet claimsSet, SecurityContext ctx) throws BadJWTException {
                String originalIssuer = claimsSet.getIssuer();
                String actualIssuer = normalizeIssuer(originalIssuer);
                log.debug("Issuer comparison - Token original: '{}', Token normalized: '{}', Expected: '{}'", 
                         originalIssuer, actualIssuer, normalizedIssuerUri);
                if (!normalizedIssuerUri.equals(actualIssuer)) {
                    throw new BadJWTException("JWT iss claim has value " + actualIssuer + " (original: " + originalIssuer + "), must be " + normalizedIssuerUri);
                }
                super.verify(claimsSet, ctx);
            }
        };
        claimsVerifier.setMaxClockSkew((int) clockSkewSeconds);

        processor.setJWTClaimsSetVerifier((claims, context) -> {
            claimsVerifier.verify(claims, context);
            validateAudienceOrAzp(claims, clientId);
        });

        return processor;
    }

    private String resolveDiscoveryUri(OidcProviderDetails providerDetails, String normalizedIssuerUri) {
        // 1. Explicitly configured discovery URI takes precedence
        if (providerDetails.getDiscoveryUri() != null && !providerDetails.getDiscoveryUri().isEmpty()) {
            log.debug("Using explicitly configured OIDC discovery URI: {}", providerDetails.getDiscoveryUri());
            return providerDetails.getDiscoveryUri();
        }
        
        return OidcUtils.resolveDiscoveryUri(normalizedIssuerUri);
    }

    private DefaultResourceRetriever createResourceRetriever() {
        var jwks = oidcProperties.jwks();

        return new ConfigurableResourceRetriever(
                (int) jwks.connectTimeout().toMillis(),
                (int) jwks.readTimeout().toMillis(),
                jwks.sizeLimit(),
                new ConfigurableResourceRetriever.RetrieverConfig(
                        jwks.proxyHost(),
                        jwks.proxyPort(),
                        jwks.userAgent(),
                        jwks.proxyUsername(),
                        jwks.proxyPassword()
                )
        );
    }

    private DiscoveryConfiguration fetchDiscoveryConfiguration(String discoveryUri, DefaultResourceRetriever retriever) {
        try {
            var resource = retriever.retrieveResource(URI.create(discoveryUri).toURL());

            String contentType = resource.getContentType();
            if (contentType != null && !contentType.contains("application/json") && !contentType.contains("application/jrd+json")) {
                throw new IllegalStateException("Invalid content type '" + contentType + "' from OIDC discovery document at " + discoveryUri +
                    ". Expected application/json or application/jrd+json");
            }

            String json = resource.getContent();

            var mapper = new ObjectMapper();
            var root = mapper.readTree(json);

            if (root == null || root.isMissingNode()) {
                throw new IllegalStateException("Invalid OIDC discovery document format from " + discoveryUri);
            }

            String jwksUriStr = root.path("jwks_uri").asText();
            if (jwksUriStr == null || jwksUriStr.isEmpty()) {
                throw new IllegalStateException("jwks_uri not found in OIDC discovery document from " + discoveryUri);
            }

            try {
                URI jwksUri = URI.create(jwksUriStr);
                String discoveryIssuer = root.path("issuer").asText(null);
                Set<JWSAlgorithm> algorithms = extractSupportedAlgorithms(root);

                log.info("OIDC Discovery Loaded from {}: Issuer='{}', JWKS='{}', Algs={}",
                         discoveryUri, discoveryIssuer, jwksUriStr, algorithms);

                if (algorithms.isEmpty()) {
                     log.warn("Provider did not advertise supported algorithms. Defaulting to RS256. If using Authentik, check 'Signing Key' settings.");
                }

                return new DiscoveryConfiguration(jwksUri, algorithms, discoveryIssuer);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid JWKS URI format in OIDC discovery document from " + discoveryUri + ": " + jwksUriStr, e);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to fetch OIDC discovery document from " + discoveryUri, e);
        }
    }

    private Set<JWSAlgorithm> extractSupportedAlgorithms(com.fasterxml.jackson.databind.JsonNode root) {
        var algorithms = new LinkedHashSet<JWSAlgorithm>();
        String[] fieldsToInspect = {
                "id_token_signing_alg_values_supported",
                "userinfo_signing_alg_values_supported",
                "token_endpoint_auth_signing_alg_values_supported"
        };

        for (String field : fieldsToInspect) {
            var node = root.path(field);
            if (node != null && node.isArray()) {
                node.forEach(value -> {
                    if (value.isTextual()) {
                        String algValue = value.asText();
                        JWSAlgorithm algorithm = JWSAlgorithm.parse(algValue);
                        if (KNOWN_JWS_ALGORITHMS.contains(algorithm)) {
                            algorithms.add(algorithm);
                        } else {
                            log.warn("Ignoring unrecognised or unsupported JWS algorithm '{}' advertised by provider", algValue);
                        }
                    }
                });
            }
        }

        if (algorithms.isEmpty()) {
            log.debug("Discovery document did not advertise signing algorithms; defaulting to {}", JWSAlgorithm.RS256);
            algorithms.add(JWSAlgorithm.RS256);
        }

        return Collections.unmodifiableSet(algorithms);
    }

    private Set<JWSAlgorithm> filterAlgorithmsByJwks(JWKSet jwkSet, URI jwksUri, Set<JWSAlgorithm> advertisedAlgorithms) {
        try {
            boolean hasRsa = false;
            boolean hasEc = false;
            boolean hasOct = false;
            boolean hasOkp = false;
            
            int totalKeys = jwkSet.getKeys().size();
            log.debug("Inspecting {} keys in JWKS at {}", totalKeys, jwksUri);

            for (JWK jwk : jwkSet.getKeys()) {
                KeyType keyType = jwk.getKeyType();
                String kid = jwk.getKeyID();
                String use = jwk.getKeyUse() != null ? jwk.getKeyUse().getValue() : "unspecified";
                String alg = jwk.getAlgorithm() != null ? jwk.getAlgorithm().getName() : "unspecified";
                
                log.debug("  Key: kid={}, kty={}, use={}, alg={}", kid, keyType, use, alg);
                
                if (KeyType.RSA.equals(keyType)) {
                    hasRsa = true;
                } else if (KeyType.EC.equals(keyType)) {
                    hasEc = true;
                } else if (KeyType.OCT.equals(keyType)) {
                    hasOct = true;
                    log.warn("JWKS contains OCT (symmetric) key with kid={}. This is unusual for OIDC providers.", kid);
                } else if (KeyType.OKP.equals(keyType)) {
                    hasOkp = true;
                }
            }
            
            log.info("JWKS Key Types Summary: RSA={}, EC={}, OCT={}, OKP={}", hasRsa, hasEc, hasOct, hasOkp);

            final boolean finalHasRsa = hasRsa;
            final boolean finalHasEc = hasEc;
            final boolean finalHasOct = hasOct;
            final boolean finalHasOkp = hasOkp;

            Set<JWSAlgorithm> defaultRsaAlgs = Set.of(JWSAlgorithm.RS256, JWSAlgorithm.PS256);
            Set<JWSAlgorithm> defaultEcAlgs = Set.of(JWSAlgorithm.ES256, JWSAlgorithm.ES384);
            Set<JWSAlgorithm> defaultHmacAlgs = Set.of(JWSAlgorithm.HS256, JWSAlgorithm.HS512);
            Set<JWSAlgorithm> defaultEddsaAlgs = Set.of(JWSAlgorithm.EdDSA);

            Set<JWSAlgorithm> filtered = advertisedAlgorithms.stream()
                .filter(alg -> (isRsaAlgorithm(alg) && finalHasRsa) || (isEcAlgorithm(alg) && finalHasEc) || (isHmacAlgorithm(alg) && finalHasOct) || (JWSAlgorithm.EdDSA.equals(alg) && finalHasOkp))
                .collect(Collectors.toCollection(LinkedHashSet::new));

            if (filtered.isEmpty()) {
                if (hasRsa) {
                    filtered.addAll(defaultRsaAlgs);
                } else if (hasEc) {
                    filtered.addAll(defaultEcAlgs);
                } else if (hasOkp) {
                    filtered.addAll(defaultEddsaAlgs);
                } else if (hasOct) {
                    filtered.addAll(defaultHmacAlgs);
                } else {
                    log.warn("JWKS at {} did not expose any keys. Falling back to advertised algorithms: {}", jwksUri, advertisedAlgorithms);
                    return advertisedAlgorithms;
                }
                log.warn("Advertised algorithms {} did not match JWKS key types at {}. Using inferred set: {}", advertisedAlgorithms, jwksUri, filtered);
            } else if (!filtered.equals(advertisedAlgorithms)) {
                log.info("Filtered advertised algorithms {} down to {} based on JWKS key types at {}", advertisedAlgorithms, filtered, jwksUri);
            }

            return Collections.unmodifiableSet(filtered);
        } catch (Exception e) {
            log.warn("Could not inspect JWKS at {} to filter algorithms: {}. Using advertised algorithms {}", jwksUri, e.getMessage(), advertisedAlgorithms);
            return advertisedAlgorithms;
        }
    }

    private static boolean isHmacAlgorithm(JWSAlgorithm alg) {
        return JWSAlgorithm.HS256.equals(alg) || JWSAlgorithm.HS384.equals(alg) || JWSAlgorithm.HS512.equals(alg);
    }

    private static boolean isRsaAlgorithm(JWSAlgorithm alg) {
        return JWSAlgorithm.RS256.equals(alg) || JWSAlgorithm.RS384.equals(alg) || JWSAlgorithm.RS512.equals(alg)
            || JWSAlgorithm.PS256.equals(alg) || JWSAlgorithm.PS384.equals(alg) || JWSAlgorithm.PS512.equals(alg);
    }

    private static boolean isEcAlgorithm(JWSAlgorithm alg) {
        return JWSAlgorithm.ES256.equals(alg) || JWSAlgorithm.ES256K.equals(alg)
            || JWSAlgorithm.ES384.equals(alg) || JWSAlgorithm.ES512.equals(alg);
    }

    private void validateAudienceOrAzp(JWTClaimsSet claims, String clientId) throws BadJWTException {
        List<String> audiences = claims.getAudience();
        if (audiences != null && audiences.contains(clientId)) {
            return;
        }

        // In strict mode, azp fallback is not allowed
        if (oidcProperties.strictAudienceValidation()) {
            log.error("SECURITY: Strict audience validation failed. Expected ClientID '{}' in 'aud' claim. " +
                     "Token Claims - aud: {}, azp: {}. Fallback to 'azp' is disabled in strict mode.",
                     clientId, audiences, claims.getClaim("azp"));
            throw new BadJWTException("JWT 'aud' claim must contain client ID '" + clientId + "' (strict validation mode)");
        }

        Object azp = claims.getClaim("azp");
        if (azp instanceof String authorizedParty && clientId.equals(authorizedParty)) {
            log.debug("JWT audience did not include client id {}, but azp matched. Accepting token (non-strict mode).", clientId);
            return;
        }

        log.error("Token validation FAILED: Audience mismatch. Expected ClientID: '{}'. Token Claims - aud: {}, azp: {}. Neither matched.",
            clientId, audiences, azp);
        
        throw new BadJWTException("JWT audience mismatch: expected audience or azp to match client ID '" + clientId + "'");
    }

    private String normalizeIssuer(String issuer) {
        if (issuer == null) return null;

        String normalizeIssuerUri = normalizeIssuerUri(issuer);

        if (!oidcProperties.allowIssuerProtocolMismatch()
                || this.currentIssuerUri == null) {
            return normalizeIssuerUri;
        }
        if (isProtocolUpgrade(normalizeIssuerUri, this.currentIssuerUri)) {
            log.warn("JWT issuer protocol upgrade detected. Issuer: {}, Config: {}. " +
                    "Upgrading to HTTPS due to allowIssuerProtocolMismatch=true.",
                    normalizeIssuerUri, this.currentIssuerUri);
            return normalizeIssuerUri.replace("http://", "https://");
        }

        return normalizeIssuerUri;
    }
    
    private boolean isDevelopmentEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length > 0 && 
               (Arrays.asList(activeProfiles).contains("dev") || 
                Arrays.asList(activeProfiles).contains("development") || 
                Arrays.asList(activeProfiles).contains("local"));
    }

    private static boolean isProtocolUpgrade(String tokenIssuer, String configIssuer) {
        try {
            URI tokenUri = new URI(tokenIssuer);
            URI configUri = new URI(configIssuer);
            if (!"http".equals(tokenUri.getScheme()) || !"https".equals(configUri.getScheme())) {
                return false;
            }
            
            return tokenUri.getHost().equals(configUri.getHost())
                    && normalizePath(tokenUri.getPath()).equals(normalizePath(configUri.getPath()));
        } catch (URISyntaxException e) {
            log.debug("Invalid URI syntax during protocol upgrade check: {}", e.getMessage());
            return false;
        }
    }
    
    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return path.charAt(path.length() - 1) == '/' ? path.substring(0, path.length() - 1) : path;
    }

    private static String normalizeIssuerUri(String issuerUri) {
        return OidcUtils.normalizeIssuerUri(issuerUri);
    }

    private static final class DiscoveryConfiguration {
        private final URI jwksUri;
        private final Set<JWSAlgorithm> supportedAlgorithms;
        private final String issuer;

        private DiscoveryConfiguration(URI jwksUri, Set<JWSAlgorithm> supportedAlgorithms, String issuer) {
            this.jwksUri = jwksUri;
            this.supportedAlgorithms = supportedAlgorithms;
            this.issuer = issuer;
        }

        private URI jwksUri() {
            return jwksUri;
        }

        private Set<JWSAlgorithm> supportedAlgorithms() {
            return supportedAlgorithms;
        }

        private String issuer() {
            return issuer;
        }
    }

    private static class ConfigurableResourceRetriever extends DefaultResourceRetriever {
        private static class RetrieverConfig {
            private final String proxyHost;
            private final Integer proxyPort;
            private final String userAgent;
            private final String proxyUser;
            private final String proxyPassword;

            public RetrieverConfig(String proxyHost, Integer proxyPort, String userAgent, String proxyUser, String proxyPassword) {
                this.proxyHost = proxyHost;
                this.proxyPort = proxyPort;
                this.userAgent = userAgent;
                this.proxyUser = proxyUser;
                this.proxyPassword = proxyPassword;
            }

            public String getProxyHost() { return proxyHost; }
            public Integer getProxyPort() { return proxyPort; }
            public String getUserAgent() { return userAgent; }
            public String getProxyUser() { return proxyUser; }
            public String getProxyPassword() { return proxyPassword; }
        }

        private final RetrieverConfig config;

        public ConfigurableResourceRetriever(int connectTimeout, int readTimeout, int sizeLimit, RetrieverConfig config) {
            super(connectTimeout, readTimeout, sizeLimit);
            this.config = config;
        }

        @Override
        public Resource retrieveResource(URL url) throws IOException {
            HttpURLConnection connection;
            if (config.getProxyHost() != null && !config.getProxyHost().isEmpty() && config.getProxyPort() != null) {
                log.debug("Opening connection to {} via proxy {}:{}", url, config.getProxyHost(), config.getProxyPort());
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.getProxyHost(), config.getProxyPort()));
                connection = (HttpURLConnection) url.openConnection(proxy);
            } else {
                log.debug("Opening direct connection to {}", url);
                connection = (HttpURLConnection) url.openConnection();
            }

            connection.setConnectTimeout(getConnectTimeout());
            connection.setReadTimeout(getReadTimeout());

            String userAgentStr = config.getUserAgent() != null && !config.getUserAgent().isEmpty() ? config.getUserAgent() : "BookLore-OIDC-Client/1.0";
            connection.setRequestProperty("User-Agent", userAgentStr);

            if (config.getProxyUser() != null && !config.getProxyUser().isEmpty() && config.getProxyPassword() != null) {
                String auth = config.getProxyUser() + ":" + config.getProxyPassword();
                String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                connection.setRequestProperty("Proxy-Authorization", "Basic " + encodedAuth);
            }

            try {
                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    String errorMsg = connection.getResponseMessage();
                    String errorBody = "";
                    try (InputStream errorStream = connection.getErrorStream()) {
                        if (errorStream != null) {
                            errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    } catch (IOException e) {
                        log.debug("Could not read error stream for {}: {}", url, e.getMessage());
                    }
                    String fullError = String.format("HTTP %d%s from %s. Body: %s",
                            responseCode,
                            (errorMsg != null ? " (" + errorMsg + ")" : ""),
                            url,
                            errorBody.isEmpty() ? "(empty)" : errorBody.chars()
                                    .filter(c -> c >= 32 && c <= 126) // Printable ASCII only
                                    .limit(500) // Limit length
                                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                    .toString());
                    
                    log.warn("JWKS fetching failed: {}", fullError); // Log at warn level for visibility
                    throw new IOException(fullError);
                }

                try (InputStream inputStream = connection.getInputStream()) {
                    int sizeLimit = getSizeLimit();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(sizeLimit, 8192));
                    byte[] chunk = new byte[8192];
                    int bytesRead;
                    int totalRead = 0;

                    while ((bytesRead = inputStream.read(chunk)) != -1) {
                        if (totalRead + bytesRead > sizeLimit) {
                            throw new IOException("Resource size exceeds limit " + sizeLimit);
                        }
                        buffer.write(chunk, 0, bytesRead);
                        totalRead += bytesRead;
                    }

                    byte[] content = buffer.toByteArray();
                    String contentType = connection.getContentType();
                    log.debug("Successfully retrieved resource from {}. Size: {} bytes, Content-Type: {}", url, content.length, contentType);
                    return new Resource(new String(content, StandardCharsets.UTF_8),
                        contentType != null ? contentType : "application/octet-stream");
                }
            } finally {
                connection.disconnect();
            }
        }
    }

    @PostConstruct
    public void warmUpOidcConfiguration() {
        if (healthCheckCompleted) {
            return;
        }
        
        try {
            var appSettings = appSettingService.getAppSettings();
            if (appSettings.isOidcEnabled() && appSettings.getOidcProviderDetails() != null) {
                log.info("Warming up OIDC configuration at startup...");
                getProcessor(); // This will fetch JWKS and validate configuration
                healthCheckCompleted = true;
                log.info("OIDC configuration validated successfully - provider is reachable");
            }
        } catch (Exception e) {
            log.error("OIDC provider not reachable at startup. First authentication requests may fail until provider is available: {}", 
                     e.getMessage());
        }
    }

    public boolean isHealthy() {
        try {
            getProcessor();
            return true;
        } catch (Exception e) {
            log.debug("OIDC health check failed: {}", e.getMessage());
            return false;
        }
    }

    public String getHealthStatus() {
        try {
            getProcessor();
            return "OIDC provider reachable and configured correctly";
        } catch (Exception e) {
            return "OIDC provider unreachable or misconfigured: " + e.getMessage();
        }
    }

    private static String mask(String text) {
        if (text == null) return "null";
        if (text.length() <= 4) return "***";
        return text.substring(0, 2) + "***" + text.substring(text.length() - 2);
    }

    /**
     * Extracts groups/roles from JWT claims based on the configured claim path.
     * Supports various OIDC providers:
     * - Authentik/Authelia/PocketID: "groups" claim (array of strings)
     * - Keycloak realm roles: "realm_access.roles" (nested object)
     * - Keycloak client roles: "resource_access.CLIENT_ID.roles" (nested object)
     * 
     * @param claimsSet The validated JWT claims
     * @param groupsClaimName The claim name/path to extract groups from
     * @return List of group/role names, or empty list if not found
     */
    @SuppressWarnings("unchecked")
    public List<String> extractGroups(JWTClaimsSet claimsSet, String groupsClaimName) {
        if (groupsClaimName == null || groupsClaimName.isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            if (groupsClaimName.contains(".")) {
                String[] parts = groupsClaimName.split("\\.");
                Object current = claimsSet.getClaims();

                for (String part : parts) {
                    if (current instanceof java.util.Map) {
                        current = ((java.util.Map<String, Object>) current).get(part);
                    } else {
                        log.debug("Cannot traverse claim path '{}' at part '{}' - current value is not a map",
                                groupsClaimName, part);
                        return Collections.emptyList();
                    }

                    if (current == null) {
                        log.debug("Claim path '{}' not found at part '{}'", groupsClaimName, part);
                        return Collections.emptyList();
                    }
                }
                
                return extractGroupsFromValue(current, groupsClaimName);
            }
            
            Object groupsValue = claimsSet.getClaim(groupsClaimName);
            return extractGroupsFromValue(groupsValue, groupsClaimName);
            
        } catch (Exception e) {
            log.warn("Failed to extract groups from claim '{}': {}", groupsClaimName, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Extracts group/role names from a JWT claim value.
     * Handles various formats:
     * <ul>
     *   <li>List of strings: ["admin", "user"]</li>
     *   <li>JSON array string: "[\"admin\", \"user\"]"</li>
     *   <li>Comma-separated string: "admin, user"</li>
     *   <li>Single value: "admin"</li>
     * </ul>
     *
     * @param value The claim value (may be null)
     * @param claimName The name of the claim (for logging)
     * @return List of group names, never null
     */
    public static List<String> extractGroupsFromValue(Object value, String claimName) {
        switch (value) {
            case null -> {
                return Collections.emptyList();
            }
            case List<?> list -> {
                List<String> groups = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String str) {
                        groups.add(str);
                    } else if (item != null) {
                        groups.add(item.toString());
                    }
                }
                log.debug("Extracted {} groups from claim '{}': {}", groups.size(), claimName, groups);
                return groups;
            }
            case String str -> {
                return parseStringValue(str, claimName);
            }
            default -> {
                log.debug("Unexpected type for groups claim '{}': {}", claimName, value.getClass().getName());
                return Collections.emptyList();
            }
        }
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static List<String> parseStringValue(String str, String claimName) {
        str = str.trim();

        if (str.charAt(0) == '[' && !str.isEmpty() && str.charAt(str.length() - 1) == ']') {
            try {
                List<String> parsed = JSON_MAPPER.readValue(str, new TypeReference<>() {
                });
                log.debug("Parsed JSON array from claim '{}': {}", claimName, parsed);
                return parsed;
            } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
                log.warn("Failed to parse JSON array from claim '{}': {}", claimName, e.getMessage());
                // Fall through to comma-separated parsing
            }
        }

        if (str.contains(",")) {
            return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        return List.of(str);
    }
}
