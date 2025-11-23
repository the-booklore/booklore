package com.adityachandel.booklore.config.security.service;

import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
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
import org.springframework.stereotype.Component;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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

    private volatile ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private volatile String currentIssuerUri;
    private volatile String currentClientId;

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
        if (localRef == null || !Objects.equals(normalizedIssuerUri, currentIssuerUri) || !Objects.equals(clientId, currentClientId)) {
            synchronized (this) {
                localRef = jwtProcessor;
                if (localRef == null || !Objects.equals(normalizedIssuerUri, currentIssuerUri) || !Objects.equals(clientId, currentClientId)) {
                    log.info("OIDC configuration change detected (Old Issuer: {}, New Issuer: {}). Rebuilding JWT Processor.", currentIssuerUri, normalizedIssuerUri);
                    this.jwtProcessor = buildProcessor(providerDetails, normalizedIssuerUri);
                    this.currentIssuerUri = normalizedIssuerUri;
                    this.currentClientId = clientId;
                    localRef = this.jwtProcessor;
                }
            }
        }
        return localRef;
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
                log.warn("Issuer mismatch between configuration ({}) and discovery document ({}). Proceeding with configured issuer.", normalizedIssuerUri, discoveredIssuer);
            }
        }

        URI jwksUri = discoveryConfiguration.jwksUri();

    Set<JWSAlgorithm> supportedAlgorithms = discoveryConfiguration.supportedAlgorithms().isEmpty()
        ? Set.of(JWSAlgorithm.RS256)
                : discoveryConfiguration.supportedAlgorithms();

        log.debug("Configuring JWKS retrieval from {} with timeouts: connect={}ms, read={}ms, sizeLimit={}bytes, algorithms={}",
                jwksUri, oidcProperties.jwks().connectTimeout().toMillis(), oidcProperties.jwks().readTimeout().toMillis(), oidcProperties.jwks().sizeLimit(), supportedAlgorithms);

        var jwkSourceBuilder = JWKSourceBuilder
                .create(jwksUri.toURL(), resourceRetriever)
                .cache(oidcProperties.jwks().cacheTtl().toMillis(), oidcProperties.jwks().cacheRefresh().toMillis());

        if (!oidcProperties.jwks().rateLimitEnabled()) {
            jwkSourceBuilder.rateLimited(false);
            log.debug("JWKS rate limiting disabled for internal network communication");
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

        var claimsVerifier = new DefaultJWTClaimsVerifier<>(expectedClaims, Set.of("sub", "exp"));
        claimsVerifier.setMaxClockSkew(Math.toIntExact(clockSkewSeconds));

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
        }

        return Collections.unmodifiableSet(algorithms);
    }

    private void validateAudienceOrAzp(JWTClaimsSet claims, String clientId) throws BadJWTException {
        List<String> audiences = claims.getAudience();
        if (audiences != null && audiences.contains(clientId)) {
            return;
        }

        Object azp = claims.getClaim("azp");
        if (azp instanceof String authorizedParty && clientId.equals(authorizedParty)) {
            log.debug("JWT audience did not include client id {}, but azp matched. Accepting token.", clientId);
            return;
        }

        log.warn("Token validation failed: Audience mismatch. Expected ClientID: '{}'. Token Claims - aud: {}, azp: {}",
            clientId, audiences, azp);
        throw new BadJWTException("JWT does not contain expected audience or azp for client '" + clientId + "'");
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
}
