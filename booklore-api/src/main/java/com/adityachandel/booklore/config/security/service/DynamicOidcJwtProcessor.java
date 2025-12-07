package com.adityachandel.booklore.config.security.service;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        if (!Objects.equals(issuerUri, normalizedIssuerUri)) {
            log.debug("Normalized issuer URI from '{}' to '{}'", issuerUri, normalizedIssuerUri);
        }
        String clientId = providerDetails.getClientId();

        ConfigurableJWTProcessor<SecurityContext> localRef = jwtProcessor;
        
        // Check if update is needed
        if (localRef == null || !Objects.equals(normalizedIssuerUri, currentIssuerUri) || !Objects.equals(clientId, currentClientId)) {
            
            log.info("OIDC configuration change detected (Old Issuer: {}, New Issuer: {}). Fetching configuration...", currentIssuerUri, normalizedIssuerUri);
            
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

    private void logTokenMetadata(String token) {
        try {
            // Parse without verifying signature just for inspection
            var jwt = com.nimbusds.jwt.SignedJWT.parse(token);
            var claims = jwt.getJWTClaimsSet();
            var header = jwt.getHeader();

            // Calculate time deltas for easier debugging
            var now = new Date();
            long secondsLeft = claims.getExpirationTime() != null
                ? (claims.getExpirationTime().getTime() - now.getTime()) / 1000
                : 0;

            log.info("Processing JWT - Header: [alg={}, kid={}]; Payload: [iss={}, sub=***, aud={}, azp={}, exp_in={}s]",
                    header.getAlgorithm(),
                    header.getKeyID(),
                    claims.getIssuer(),
                    // distinct log for audiences list
                    claims.getAudience(),
                    claims.getClaim("azp"),
                    secondsLeft
            );

            // Check for potential clock skew issues immediately
            if (secondsLeft < 0) {
                log.warn("Token received is already expired by {} seconds. Check server clock sync.", Math.abs(secondsLeft));
            }

        } catch (Exception e) {
            log.warn("Received token that could not be parsed for logging: {}", e.getMessage());
        }
    }

    public JWTClaimsSet process(String token) throws Exception {
        // 1. Log safe metadata
        logTokenMetadata(token);

        // Acquire read lock for concurrent token processing
        readLock.lock();
        try {
            // 2. Execute verification
            JWTClaimsSet claimsSet;
            try {
                claimsSet = getProcessor().process(token, null);
            } catch (BadJOSEException e) {
                // Check if this is an algorithm mismatch (e.g., HS256 token with RSA keys)
                if (e.getMessage() != null && e.getMessage().contains("Another algorithm expected")) {
                    log.warn("ALGORITHM MISMATCH DETECTED: Token algorithm doesn't match JWKS key types. Attempting dynamic fallback...");
                    claimsSet = handleAlgorithmMismatch(token, e);
                } else {
                    throw e;
                }
            }

            // 3. Check for token replay (JTI-based prevention)
            if (oidcProperties.jwt().enableReplayPrevention()) {
                String jti = claimsSet.getJWTID();
                if (jti != null && !jti.isEmpty()) {
                    Cache<String, Boolean> localCache = usedTokenCache;
                    if (localCache == null) {
                        // Need write lock for cache initialization
                        readLock.unlock();
                        writeLock.lock();
                        try {
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
                            // Downgrade to read lock for the rest of the processing
                            readLock.lock();
                        } finally {
                            writeLock.unlock();
                        }
                    }

                    // Check if token was already used
                    if (localCache.getIfPresent(jti) != null) {
                        log.error("SECURITY: Token replay attempt detected. JTI: {}", jti);
                        throw new BadJWTException("Token has already been used (replay attack prevention)");
                    }

                    localCache.put(jti, Boolean.TRUE);
                    log.debug("Token JTI '{}' cached to prevent replay", jti);
                } else {
                    log.error("SECURITY: Token does not contain JTI claim. Replay prevention is enabled but JTI is required for security.");
                    throw new BadJWTException("Token missing JTI claim (required for replay prevention)");
                }
            }

            return claimsSet;
        } catch (BadJWTException e) {
            // 4. Contextualize the error without leaking the token
            log.error("JWT Verification Failed: {}", e.getMessage());

            // Add hints for common configuration errors
            if (e.getMessage().contains("Issuer")) {
                log.error("Issuer Mismatch Hint: Configured='{}' vs Token Claim. Check trailing slashes or http/https.", currentIssuerUri);
            } else if (e.getMessage().contains("Audience")) {
                log.error("Audience Mismatch Hint: Configured ClientID='{}'. Ensure your OIDC provider maps this Client ID to the 'aud' claim.", currentClientId);
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
     * This is a fallback for providers (like Authentik) that may sign with HS256 but advertise RSA keys.
     */
    private JWTClaimsSet handleAlgorithmMismatch(String token, BadJOSEException originalException) throws Exception {
        try {
            // Parse token to inspect actual algorithm
            var jwt = com.nimbusds.jwt.SignedJWT.parse(token);
            var header = jwt.getHeader();
            var algorithm = header.getAlgorithm();
            
            log.warn("Token uses algorithm '{}' which is not supported by current JWKS keys. " +
                    "This typically indicates an IdP misconfiguration.", algorithm);
            
            // Check if it's an HMAC algorithm (HS256, HS384, HS512)
            if (isHmacAlgorithm(algorithm)) {
                log.error("CONFIGURATION ERROR: Token signed with HMAC algorithm '{}' but JWKS contains asymmetric keys. " +
                        "HMAC algorithms require a shared secret and cannot be validated via JWKS. " +
                        "ACTION REQUIRED: Configure your OIDC provider to use RS256 or another asymmetric algorithm.", algorithm);
                
                log.error("SECURITY IMPACT: HMAC tokens cannot be securely validated without the shared secret. " +
                        "This is a critical security issue. The provider MUST be reconfigured to use RSA/EC signing.");
            } else if (isEcAlgorithm(algorithm)) {
                log.warn("Token uses EC algorithm '{}' but JWKS may only contain RSA keys. " +
                        "Verify your OIDC provider's JWKS includes EC keys.", algorithm);
            } else {
                log.warn("Token uses unexpected algorithm '{}'. Supported algorithms: [RS256, RS384, RS512, PS256, PS384, PS512, ES256, ES384, ES512]", 
                        algorithm);
            }
            
            // Log recommendation
            log.info("RECOMMENDATION: Update IdP configuration at issuer='{}' to sign tokens with RS256 algorithm " +
                    "matching the public keys in JWKS endpoint.", currentIssuerUri);
            
        } catch (Exception parseEx) {
            log.error("Failed to parse token header for algorithm inspection: {}", parseEx.getMessage());
        }
        
        // Re-throw original exception with additional context
        throw new BadJOSEException("JWT algorithm mismatch: Token algorithm does not match available JWKS keys. " +
                "This is typically caused by IdP misconfiguration (e.g., signing with HS256 but advertising RSA keys). " +
                "Please reconfigure your OIDC provider to use RS256 signing.", originalException);
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

        String discoveryUri = normalizedIssuerUri + "/.well-known/openid-configuration";
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

        Set<JWSAlgorithm> supportedAlgorithms = discoveryConfiguration.supportedAlgorithms().isEmpty()
            ? Set.of(JWSAlgorithm.RS256)
            : discoveryConfiguration.supportedAlgorithms();

        // Filter advertised algorithms against the actual key material to avoid alg/key mismatches (e.g. HS tokens with RSA keys)
        supportedAlgorithms = filterAlgorithmsByJwks(jwksUri, resourceRetriever, supportedAlgorithms);

        log.debug("Configuring JWKS retrieval from {} with timeouts: connect={}ms, read={}ms, sizeLimit={}bytes, algorithms={}",
                jwksUri, oidcProperties.jwks().connectTimeout().toMillis(), oidcProperties.jwks().readTimeout().toMillis(), oidcProperties.jwks().sizeLimit(), supportedAlgorithms);

        var jwkSourceBuilder = JWKSourceBuilder
                .create(jwksUri.toURL(), resourceRetriever)
                .cache(oidcProperties.jwks().cacheTtl().toMillis(), oidcProperties.jwks().cacheRefresh().toMillis());

        // Configure rate limiting based on security settings
        if (oidcProperties.jwks().rateLimitEnabled()) {
            jwkSourceBuilder.rateLimited(true);
            log.debug("JWKS rate limiting enabled for security (recommended for production)");
        } else {
            jwkSourceBuilder.rateLimited(false);
            log.warn("JWKS rate limiting disabled. This reduces protection against JWKS endpoint flooding attacks. " +
                    "Only disable for high-trust internal networks with alternative DDoS protection.");
        }

        var jwkSource = jwkSourceBuilder.build();

        var keySelector = new JWSVerificationKeySelector<>(supportedAlgorithms, jwkSource);
        var processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(keySelector);

        long clockSkewSeconds = oidcProperties.jwt().clockSkew().toSeconds();
        log.debug("JWT clock skew tolerance set to {}s for handling clock drift between services", clockSkewSeconds);

        var expectedClaims = new JWTClaimsSet.Builder()
                .issuer(normalizedIssuerUri)
                .build();

        var claimsVerifier = new DefaultJWTClaimsVerifier<>(expectedClaims, Set.of("sub", "exp")) {
            @Override
            public void verify(JWTClaimsSet claimsSet, SecurityContext ctx) throws BadJWTException {
                String actualIssuer = normalizeIssuer(claimsSet.getIssuer(), true);
                if (!normalizedIssuerUri.equals(actualIssuer)) {
                    throw new BadJWTException("JWT iss claim has value " + claimsSet.getIssuer() + ", must be " + normalizedIssuerUri);
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

    private DefaultResourceRetriever createResourceRetriever() {
        var jwks = oidcProperties.jwks();

        return new ConfigurableResourceRetriever(
                (int) jwks.connectTimeout().toMillis(),
                (int) jwks.readTimeout().toMillis(),
                jwks.sizeLimit(),
                jwks.proxyHost(),
                jwks.proxyPort(),
                jwks.userAgent(),
                jwks.proxyUser(),
                jwks.proxyPassword()
        );
    }

    private DiscoveryConfiguration fetchDiscoveryConfiguration(String discoveryUri, DefaultResourceRetriever retriever) {
        try {
            var resource = retriever.retrieveResource(URI.create(discoveryUri).toURL());

            // Validate content type is JSON
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

                // --- ADD THIS LOGGING ---
                log.info("OIDC Discovery Loaded from {}: Issuer='{}', JWKS='{}', Algs={}",
                         discoveryUri, discoveryIssuer, jwksUriStr, algorithms);

                if (algorithms.isEmpty()) {
                     log.warn("Provider did not advertise supported algorithms. Defaulting to RS256. If using Authentik, check 'Signing Key' settings.");
                }
                // ------------------------

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

    private Set<JWSAlgorithm> filterAlgorithmsByJwks(URI jwksUri, DefaultResourceRetriever retriever, Set<JWSAlgorithm> advertisedAlgorithms) {
        try {
            var resource = retriever.retrieveResource(jwksUri.toURL());
            var jwkSet = JWKSet.parse(resource.getContent());

            boolean hasRsa = false;
            boolean hasEc = false;
            boolean hasOct = false;
            boolean hasOkp = false;

            for (JWK jwk : jwkSet.getKeys()) {
                KeyType keyType = jwk.getKeyType();
                if (KeyType.RSA.equals(keyType)) {
                    hasRsa = true;
                } else if (KeyType.EC.equals(keyType)) {
                    hasEc = true;
                } else if (KeyType.OCT.equals(keyType)) {
                    hasOct = true;
                } else if (KeyType.OKP.equals(keyType)) {
                    hasOkp = true;
                }
            }

            Set<JWSAlgorithm> filtered = new LinkedHashSet<>();
            for (JWSAlgorithm alg : advertisedAlgorithms) {
                if (isHmacAlgorithm(alg) && hasOct) {
                    filtered.add(alg);
                } else if (isRsaAlgorithm(alg) && hasRsa) {
                    filtered.add(alg);
                } else if (isEcAlgorithm(alg) && hasEc) {
                    filtered.add(alg);
                } else if (JWSAlgorithm.EdDSA.equals(alg) && hasOkp) {
                    filtered.add(alg);
                }
            }

            if (filtered.isEmpty()) {
                // If nothing matched the actual keys, prefer asymmetric defaults based on available key types
                if (hasRsa) {
                    filtered.addAll(List.of(JWSAlgorithm.RS256, JWSAlgorithm.PS256));
                } else if (hasEc) {
                    filtered.addAll(List.of(JWSAlgorithm.ES256, JWSAlgorithm.ES384));
                } else if (hasOkp) {
                    filtered.add(JWSAlgorithm.EdDSA);
                } else if (hasOct) {
                    filtered.addAll(List.of(JWSAlgorithm.HS256, JWSAlgorithm.HS512));
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

    private boolean isHmacAlgorithm(JWSAlgorithm alg) {
        return JWSAlgorithm.HS256.equals(alg) || JWSAlgorithm.HS384.equals(alg) || JWSAlgorithm.HS512.equals(alg);
    }

    private boolean isRsaAlgorithm(JWSAlgorithm alg) {
        return JWSAlgorithm.RS256.equals(alg) || JWSAlgorithm.RS384.equals(alg) || JWSAlgorithm.RS512.equals(alg)
            || JWSAlgorithm.PS256.equals(alg) || JWSAlgorithm.PS384.equals(alg) || JWSAlgorithm.PS512.equals(alg);
    }

    private boolean isEcAlgorithm(JWSAlgorithm alg) {
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

        // Fallback to azp in non-strict mode (spec-compliant but less secure)
        Object azp = claims.getClaim("azp");
        if (azp instanceof String authorizedParty && clientId.equals(authorizedParty)) {
            log.debug("JWT audience did not include client id {}, but azp matched. Accepting token (non-strict mode).", clientId);
            return;
        }

        log.warn("Token validation failed: Audience mismatch. Expected ClientID: '{}'. Token Claims - aud: {}, azp: {}",
            clientId, audiences, azp);
        throw new BadJWTException("JWT does not contain expected audience or azp for client '" + clientId + "'");
    }

    private String normalizeIssuer(String issuer, boolean allowProtocolMismatch) {
        if (issuer == null) return null;

        // Remove trailing slashes
        issuer = normalizeIssuerUri(issuer);

        if (!allowProtocolMismatch || !oidcProperties.allowIssuerProtocolMismatch() 
                || this.currentIssuerUri == null) {
            return issuer;
        }

        // Check if this is a protocol upgrade scenario (http -> https)
        if (isProtocolUpgrade(issuer, this.currentIssuerUri)) {
            String[] activeProfiles = environment.getActiveProfiles();
            boolean isDevelopment = activeProfiles != null && activeProfiles.length > 0 &&
                (java.util.Arrays.asList(activeProfiles).contains("dev") || 
                 java.util.Arrays.asList(activeProfiles).contains("development") ||
                 java.util.Arrays.asList(activeProfiles).contains("local"));
            
            if (!isDevelopment) {
                throw new IllegalStateException(
                    "SECURITY: Protocol mismatch (HTTP to HTTPS upgrade) is only allowed in development profiles. " +
                    "Token issuer: " + issuer + ", Configured: " + this.currentIssuerUri + ". " +
                    "Either fix your OIDC provider configuration or set an active profile to 'dev', 'development', or 'local'.");
            }
            
            log.warn("DEVELOPMENT ONLY: JWT issuer protocol upgrade detected. Issuer: {}, Config: {}. " +
                    "Upgrading to HTTPS due to allowIssuerProtocolMismatch=true. " +
                    "This is ONLY allowed in development profiles.", 
                    issuer, this.currentIssuerUri);
            return issuer.replace("http://", "https://");
        }

        return issuer;
    }

    private boolean isProtocolUpgrade(String tokenIssuer, String configIssuer) {
        try {
            URI tokenUri = new URI(tokenIssuer);
            URI configUri = new URI(configIssuer);
            
            // Only allow HTTP -> HTTPS upgrade, never downgrade
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
    
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return !path.isEmpty() && path.charAt(path.length() - 1) == '/' ? path.substring(0, path.length() - 1) : path;
    }

    private static String normalizeIssuerUri(String issuerUri) {
        if (issuerUri == null) {
            return null;
        }
        int end = issuerUri.length();
        while (end > 0 && issuerUri.charAt(end - 1) == '/') {
            end--;
        }
        if (end == 0) {
            return "/";
        }
        return end == issuerUri.length() ? issuerUri : issuerUri.substring(0, end);
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
        private final String proxyHost;
        private final Integer proxyPort;
        private final String userAgent;
        private final String proxyUser;
        private final String proxyPassword;

        public ConfigurableResourceRetriever(int connectTimeout, int readTimeout, int sizeLimit, String proxyHost, Integer proxyPort,
                                             String userAgent, String proxyUser, String proxyPassword) {
            super(connectTimeout, readTimeout, sizeLimit);
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.userAgent = userAgent;
            this.proxyUser = proxyUser;
            this.proxyPassword = proxyPassword;
        }

        @Override
        public Resource retrieveResource(URL url) throws IOException {
            HttpURLConnection connection;
            if (proxyHost != null && !proxyHost.isEmpty() && proxyPort != null) {
                log.debug("Opening connection to {} via proxy {}:{}", url, proxyHost, proxyPort);
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
                connection = (HttpURLConnection) url.openConnection(proxy);
            } else {
                log.debug("Opening direct connection to {}", url);
                connection = (HttpURLConnection) url.openConnection();
            }

            connection.setConnectTimeout(getConnectTimeout());
            connection.setReadTimeout(getReadTimeout());

            // Set configurable User-Agent
            String userAgentStr = userAgent != null && !userAgent.isEmpty() ? userAgent : "BookLore-OIDC-Client/1.0";
            connection.setRequestProperty("User-Agent", userAgentStr);

            // Set proxy authentication if configured
            if (proxyUser != null && !proxyUser.isEmpty() && proxyPassword != null) {
                String auth = proxyUser + ":" + proxyPassword;
                String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                connection.setRequestProperty("Proxy-Authorization", "Basic " + encodedAuth);
            }

            try {
                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    String errorMsg = connection.getResponseMessage();
                    throw new IOException("HTTP " + responseCode +
                        (errorMsg != null ? " (" + errorMsg + ")" : "") + " from " + url);
                }

                try (InputStream inputStream = connection.getInputStream()) {
                    // Safer reading with size limit to prevent OOM attacks
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
}
