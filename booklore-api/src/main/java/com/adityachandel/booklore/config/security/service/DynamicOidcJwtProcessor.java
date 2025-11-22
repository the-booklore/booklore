package com.adityachandel.booklore.config.security.service;

import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
@Component
@EnableConfigurationProperties(OidcProperties.class)
@RequiredArgsConstructor
public class DynamicOidcJwtProcessor {
    private final AppSettingService appSettingService;
    private final OidcProperties oidcProperties;

    private volatile ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private volatile String currentIssuerUri;

    public ConfigurableJWTProcessor<SecurityContext> getProcessor() throws Exception {
        String issuerUri = appSettingService.getAppSettings().getOidcProviderDetails().getIssuerUri();

        ConfigurableJWTProcessor<SecurityContext> localRef = jwtProcessor;
        if (localRef == null || !issuerUri.equals(currentIssuerUri)) {
            synchronized (this) {
                localRef = jwtProcessor;
                if (localRef == null || !issuerUri.equals(currentIssuerUri)) {
                    this.jwtProcessor = buildProcessor(issuerUri);
                    this.currentIssuerUri = issuerUri;
                    localRef = this.jwtProcessor;
                }
            }
        }
        return localRef;
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor(String issuerUri) throws Exception {
        OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();

        if (providerDetails == null || providerDetails.getIssuerUri() == null || providerDetails.getIssuerUri().isEmpty()) {
            throw new IllegalStateException("OIDC issuer URI is not configured in app settings for issuer: " + issuerUri);
        }

        String discoveryUri = providerDetails.getIssuerUri() + "/.well-known/openid-configuration";
        log.info("Fetching OIDC discovery document from {}", discoveryUri);

        var resourceRetriever = createResourceRetriever();
        URI jwksUri = fetchJwksUri(discoveryUri, resourceRetriever);

        log.debug("Configuring JWKS retrieval from {} with timeouts: connect={}ms, read={}ms, sizeLimit={}bytes",
                jwksUri, oidcProperties.jwks().connectTimeout().toMillis(), oidcProperties.jwks().readTimeout().toMillis(), oidcProperties.jwks().sizeLimit());

        var jwkSourceBuilder = JWKSourceBuilder
                .create(jwksUri.toURL(), resourceRetriever)
                .cache(oidcProperties.jwks().cacheTtl().toMillis(), oidcProperties.jwks().cacheRefresh().toMillis());

        if (!oidcProperties.jwks().rateLimitEnabled()) {
            jwkSourceBuilder.rateLimited(false);
            log.debug("JWKS rate limiting disabled for internal network communication");
        }

        var jwkSource = jwkSourceBuilder.build();

        var keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
        var processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(keySelector);
        
        // Configure clock skew tolerance for timestamp validation (handles clock drift between services)
        long clockSkewSeconds = oidcProperties.jwt().clockSkew().toSeconds();
        log.debug("JWT clock skew tolerance set to {}s for handling clock drift between services", clockSkewSeconds);

        var expectedClaims = new JWTClaimsSet.Builder()
                .issuer(issuerUri)
                .audience(appSettingService.getAppSettings().getOidcProviderDetails().getClientId())
                .build();

        var claimsVerifier = new DefaultJWTClaimsVerifier<>(expectedClaims, Set.of("sub", "exp", "aud"));
        claimsVerifier.setMaxClockSkew(Math.toIntExact(clockSkewSeconds));
        processor.setJWTClaimsSetVerifier(claimsVerifier);

        return processor;
    }

    private DefaultResourceRetriever createResourceRetriever() {
        var jwks = oidcProperties.jwks();

        // Check if proxy is configured
        if (jwks.proxyHost() != null && !jwks.proxyHost().isEmpty() && jwks.proxyPort() != null) {
            log.debug("Configuring JWKS resource retriever with proxy: {}:{}", jwks.proxyHost(), jwks.proxyPort());
            return new ProxyResourceRetriever(
                    (int) jwks.connectTimeout().toMillis(),
                    (int) jwks.readTimeout().toMillis(),
                    jwks.sizeLimit(),
                    jwks.proxyHost(),
                    jwks.proxyPort(),
                    jwks.userAgent(),
                    jwks.proxyUser(),
                    jwks.proxyPassword()
            );
        } else {
            return new DefaultResourceRetriever(
                    (int) jwks.connectTimeout().toMillis(),
                    (int) jwks.readTimeout().toMillis(),
                    jwks.sizeLimit()
            );
        }
    }

    private static URI fetchJwksUri(String discoveryUri, DefaultResourceRetriever retriever) {
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
                return URI.create(jwksUriStr);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid JWKS URI format in OIDC discovery document from " + discoveryUri + ": " + jwksUriStr, e);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to fetch OIDC discovery document from " + discoveryUri, e);
        }
    }

    private static class ProxyResourceRetriever extends DefaultResourceRetriever {
        private final String proxyHost;
        private final int proxyPort;
        private final String userAgent;
        private final String proxyUser;
        private final String proxyPassword;

        public ProxyResourceRetriever(int connectTimeout, int readTimeout, int sizeLimit, String proxyHost, int proxyPort,
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
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);

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
                    return new Resource(new String(content, StandardCharsets.UTF_8),
                        contentType != null ? contentType : "application/octet-stream");
                }
            } finally {
                connection.disconnect();
            }
        }
    }
}
