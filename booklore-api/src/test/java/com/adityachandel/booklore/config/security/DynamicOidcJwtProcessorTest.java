package com.adityachandel.booklore.config.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.adityachandel.booklore.config.security.service.DynamicOidcJwtProcessor;
import com.adityachandel.booklore.config.security.service.OidcProperties;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@WireMockTest(httpPort = 9999)
class DynamicOidcJwtProcessorTest {

    private static final String DEFAULT_CLIENT_ID = "booklore-client";

    @Mock
    private AppSettingService appSettingService;

    private DynamicOidcJwtProcessor jwtProcessor;

    // Test helper to generate real RSA keys
    private RSAKey rsaKey;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048)
                .keyID("test-key-id")
                .generate();
        OidcProperties oidcProperties = new OidcProperties(
                new OidcProperties.Jwks(
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2),
                        1048576,
                        Duration.ofHours(6),
                        Duration.ofHours(1),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new OidcProperties.Jwt(Duration.ofSeconds(60), true, 10000),
                false,
                true,
                false
        );

        jwtProcessor = new DynamicOidcJwtProcessor(appSettingService, oidcProperties, Mockito.mock(Environment.class));

    }


    @Test
    @DisplayName("Should successfully verify a valid token signed by the provider")
    void testHappyPathVerification() throws Exception {
        setupOidcWireMock("http://localhost:9999/auth/realms/booklore", 0);

        String validToken = createSignedToken(rsaKey, "user-123", new Date(System.currentTimeMillis() + 10000));

        var securityContext = jwtProcessor.getProcessor().process(validToken, null);

        assertThat(securityContext).isNotNull();
    }

    @Test
    @DisplayName("Should rebuild processor when client ID changes")
    void shouldRebuildProcessorWhenClientIdChanges() throws Exception {
        String issuer = "http://localhost:9999/auth/realms/booklore";
        setupOidcWireMock(issuer, 0);

        AppSettings initialSettings = createAppSettings(issuer, DEFAULT_CLIENT_ID);
        AppSettings updatedSettings = createAppSettings(issuer, "booklore-client-updated");

        when(appSettingService.getAppSettings()).thenReturn(initialSettings, updatedSettings, updatedSettings);

        ConfigurableJWTProcessor<SecurityContext> initialProcessor = jwtProcessor.getProcessor();
        String tokenForInitialClient = createSignedTokenWithAudience(rsaKey, "user-123",
                new Date(System.currentTimeMillis() + 10_000L), DEFAULT_CLIENT_ID);
        assertThat(initialProcessor.process(tokenForInitialClient, null)).isNotNull();

        ConfigurableJWTProcessor<SecurityContext> updatedProcessor = jwtProcessor.getProcessor();
        String tokenForUpdatedClient = createSignedTokenWithAudience(rsaKey, "user-123",
                new Date(System.currentTimeMillis() + 10_000L), "booklore-client-updated");

        assertThat(updatedProcessor.process(tokenForUpdatedClient, null)).isNotNull();
    }

    @Test
    @DisplayName("Should reject expired tokens")
    void testExpiredToken() {
        setupOidcWireMock("http://localhost:9999/auth/realms/booklore", 0);

        String expiredToken = createSignedToken(rsaKey, "user-123", new Date(System.currentTimeMillis() - 3600000));

        assertThatThrownBy(() -> jwtProcessor.getProcessor().process(expiredToken, null))
                .isInstanceOf(BadJWTException.class)
                .hasMessageContaining("Expired JWT");
    }

    @Test
    @DisplayName("Should reject tokens signed by unknown keys")
    void testUnknownKeySignature() throws Exception {
        setupOidcWireMock("http://localhost:9999/auth/realms/booklore", 0);

        RSAKey attackerKey = new RSAKeyGenerator(2048).keyID("attacker-key").generate();
        String forgedToken = createSignedToken(attackerKey, "user-123", new Date(System.currentTimeMillis() + 10000));

        assertThatThrownBy(() -> jwtProcessor.getProcessor().process(forgedToken, null))
                .isInstanceOf(com.nimbusds.jose.proc.BadJOSEException.class)
                .hasMessageContaining("Signed JWT rejected: Another algorithm expected, or no matching key(s) found");
    }

    @Test
    @DisplayName("Should timeout when JWKS endpoint is slow (Regression Test)")
    void shouldTimeoutWhenJwksEndpointIsSlow() {
        OidcProperties shortTimeoutProperties = new OidcProperties(
                new OidcProperties.Jwks(
                        Duration.ofMillis(500),
                        Duration.ofMillis(500),
                        1048576,
                        Duration.ofHours(6),
                        Duration.ofHours(1),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new OidcProperties.Jwt(Duration.ofSeconds(60), true, 10000),
                false,
                true,
                false
        );

        DynamicOidcJwtProcessor timeoutProcessor = new DynamicOidcJwtProcessor(appSettingService, shortTimeoutProperties, Mockito.mock(Environment.class));

        setupMockSettings("http://localhost:9999/auth/realms/slow-realm");

        stubFor(get("/auth/realms/slow-realm/.well-known/openid-configuration")
                .willReturn(okJson("""
                    {
                        "issuer": "http://localhost:9999/auth/realms/slow-realm",
                        "jwks_uri": "http://localhost:9999/auth/realms/slow-realm/jwks"
                    }
                """)));

        stubFor(get("/auth/realms/slow-realm/jwks")
                .willReturn(okJson(new JWKSet(rsaKey).toPublicJWKSet().toString())
                .withFixedDelay(1000)));

        String token = createSignedToken(rsaKey, "user", new Date());

        assertThatThrownBy(() -> timeoutProcessor.getProcessor().process(token, null))
                .hasCauseInstanceOf(java.net.SocketTimeoutException.class);
    }

    @Test
    @DisplayName("Should NOT throw RateLimitReachedException on repeated failures")
    void shouldNotThrowRateLimitException() {
        setupMockSettings("http://localhost:9999/auth/realms/broken-realm");

        stubFor(get("/auth/realms/broken-realm/.well-known/openid-configuration")
                .willReturn(okJson("""
                    { "jwks_uri": "http://localhost:9999/jwks-error" }
                """)));

        stubFor(get("/jwks-error").willReturn(serverError()));

        String token = createSignedToken(rsaKey, "user", new Date());

        // Test multiple rapid requests to verify rate limiting behavior
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> jwtProcessor.getProcessor().process(token, null))
                    .isInstanceOf(Exception.class)
                    .hasRootCauseInstanceOf(java.io.IOException.class) // 500 error
                    .satisfies(throwable -> {
                        // Verify it's NOT a RateLimitReachedException
                        Throwable cause = throwable;
                        while (cause != null) {
                            if (cause instanceof com.nimbusds.jose.jwk.source.RateLimitReachedException) {
                                throw new AssertionError("Should not throw RateLimitReachedException", cause);
                            }
                            cause = cause.getCause();
                        }
                    });
        }
    }

    @Test
    @DisplayName("Should reject tokens missing required claims")
    void shouldRejectTokenWithoutSubject() throws Exception {
        setupOidcWireMock("http://localhost:9999/auth/realms/booklore", 0);

        // Create token without 'sub' claim
        String tokenWithoutSub = createSignedToken(rsaKey, null, new Date(System.currentTimeMillis() + 10000), "user-123");

        assertThatThrownBy(() -> jwtProcessor.getProcessor().process(tokenWithoutSub, null))
                .isInstanceOf(BadJWTException.class)
                .hasMessageContaining("JWT missing required claims");
    }

    @Test
    @DisplayName("Should reject tokens with wrong audience")
    void shouldRejectTokenWithWrongAudience() throws Exception {
        setupOidcWireMock("http://localhost:9999/auth/realms/booklore", 0);

        // Create token with wrong audience
        String tokenWithWrongAudience = createSignedTokenWithAudience(rsaKey, "user-123",
                new Date(System.currentTimeMillis() + 10000), "wrong-audience");

        assertThatThrownBy(() -> jwtProcessor.getProcessor().process(tokenWithWrongAudience, null))
                .isInstanceOf(BadJWTException.class)
                .hasMessageContaining("expected audience or azp");
    }

    @Test
    @DisplayName("Should reject tokens from wrong issuer")
    void shouldRejectTokenWithWrongIssuer() throws Exception {
        setupOidcWireMock("http://localhost:9999/auth/realms/booklore", 0);

        // Create token with incorrect issuer
        String tokenWithWrongIssuer = createSignedTokenWithIssuer(
                rsaKey, "user-123",
                new Date(System.currentTimeMillis() + 10000),
                "http://malicious.example.com/realms/invalid");

        assertThatThrownBy(() -> jwtProcessor.getProcessor().process(tokenWithWrongIssuer, null))
                .isInstanceOf(BadJWTException.class)
                .hasMessageContaining("JWT iss claim has value");
    }

    @Test
    @DisplayName("Should accept token when azp matches client id")
    void shouldAcceptTokenWhenAzpMatches() throws Exception {
        setupOidcWireMock("http://localhost:9999/auth/realms/booklore", 0);

        JWSSigner signer = new RSASSASigner(rsaKey);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-123")
                .issuer("http://localhost:9999/auth/realms/booklore")
                .audience("wrong-audience")
                .claim("azp", DEFAULT_CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 10000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        signedJWT.sign(signer);

        assertThat(jwtProcessor.getProcessor().process(signedJWT.serialize(), null)).isNotNull();
    }

    @Test
    @DisplayName("Should normalize issuer URIs with trailing slash")
    void shouldNormalizeIssuerWithTrailingSlash() throws Exception {
        String configuredIssuer = "http://localhost:9999/auth/realms/booklore/";
        String discoveryIssuer = "http://localhost:9999/auth/realms/booklore";
        String jwksUri = discoveryIssuer + "/protocol/openid-connect/certs";

        setupOidcWireMock(configuredIssuer, discoveryIssuer, jwksUri, 0, List.of("RS256"));

        String validToken = createSignedToken(rsaKey, "user-123", new Date(System.currentTimeMillis() + 10000));
        assertThat(jwtProcessor.getProcessor().process(validToken, null)).isNotNull();
    }

    @Test
    @DisplayName("Should accept PS256 tokens when advertised by provider")
    void shouldAcceptPs256TokensWhenAdvertised() throws Exception {
        String issuer = "http://localhost:9999/auth/realms/booklore";
        setupOidcWireMock(issuer, issuer, issuer + "/protocol/openid-connect/certs", 0, List.of("PS256", "RS256"));

        JWSSigner signer = new RSASSASigner(rsaKey);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-123")
                .issuer(issuer)
                .audience(DEFAULT_CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 10000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.PS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        signedJWT.sign(signer);

        assertThat(jwtProcessor.getProcessor().process(signedJWT.serialize(), null)).isNotNull();
    }

    @Test
    @DisplayName("Should reject tokens signed with unsupported algorithm")
    void shouldRejectUnsupportedAlgorithm() throws Exception {
        setupOidcWireMock("http://localhost:9999/auth/realms/booklore", 0);

        // Create token with HS256 instead of RS256
        String hs256Token = createSignedTokenHS256("user-123", new Date(System.currentTimeMillis() + 10000));

        assertThatThrownBy(() -> jwtProcessor.getProcessor().process(hs256Token, null))
                .isInstanceOf(com.nimbusds.jose.proc.BadJOSEException.class)
                .hasMessageContaining("Signed JWT rejected: Another algorithm expected, or no matching key(s) found");
    }

    @Test
    @DisplayName("Should handle discovery without issuer field")
    void shouldHandleDiscoveryWithoutIssuerField() throws Exception {
        String issuer = "http://localhost:9999/auth/realms/booklore";
        setupOidcWireMock(issuer, null, issuer + "/protocol/openid-connect/certs", 0, List.of());

        String validToken = createSignedToken(rsaKey, "user-123", new Date(System.currentTimeMillis() + 10000));
        assertThat(jwtProcessor.getProcessor().process(validToken, null)).isNotNull();
    }

    @Test
    @DisplayName("Should accept tokens from Keycloak with standard RS256")
    void shouldAcceptKeycloakStandardRs256() throws Exception {
        String keycloakIssuer = "http://localhost:9999/auth/realms/keycloak";
        setupOidcWireMock(keycloakIssuer, keycloakIssuer, keycloakIssuer + "/protocol/openid-connect/certs", 0,
                List.of("RS256", "PS256", "ES256"));

        String validToken = createSignedTokenWithIssuer(rsaKey, "user-123", new Date(System.currentTimeMillis() + 10000), keycloakIssuer);
        assertThat(jwtProcessor.getProcessor().process(validToken, null)).isNotNull();
    }

    @Test
    @DisplayName("Should accept tokens from Authentik with standard configuration")
    void shouldAcceptAuthentikStandardToken() throws Exception {
        String authentikIssuer = "http://localhost:9999/application/o/booklore";
        setupOidcWireMock(authentikIssuer + "/", authentikIssuer, authentikIssuer + "/.well-known/jwks.json", 0,
                List.of("RS256"));

        String validToken = createSignedTokenWithIssuer(rsaKey, "user-123", new Date(System.currentTimeMillis() + 10000), authentikIssuer);
        assertThat(jwtProcessor.getProcessor().process(validToken, null)).isNotNull();
    }

    @Test
    @DisplayName("Should accept fallback RS256 from Authelia without algorithm advertisement")
    void shouldAcceptAutheliaSilentRs256Fallback() throws Exception {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(DynamicOidcJwtProcessor.class);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            String autheliaIssuer = "http://localhost:9999";
            setupOidcWireMock(autheliaIssuer, autheliaIssuer, autheliaIssuer + "/.well-known/oauth-authorization-server/jwks", 0,
                    List.of());

            String validToken = createSignedTokenWithIssuer(rsaKey, "user-123", new Date(System.currentTimeMillis() + 10000), autheliaIssuer);
            assertThat(jwtProcessor.getProcessor().process(validToken, null)).isNotNull();

            // Verify fallback log message was emitted
            List<ILoggingEvent> logs = listAppender.list;
            assertThat(logs.stream().anyMatch(event -> event.getMessage().contains("Discovery document did not advertise signing algorithms")))
                .isTrue();
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    @ParameterizedTest
    @MethodSource("providerIssuers")
    @DisplayName("All providers should reject expired tokens")
    void shouldRejectExpiredTokensAcrossAllProviders(String issuer) throws Exception {
        setupOidcWireMock(issuer, 0);
        String expiredToken = createSignedTokenWithIssuer(rsaKey, "user",
            new Date(System.currentTimeMillis() - 3600000), issuer);

        assertThatThrownBy(() -> jwtProcessor.getProcessor().process(expiredToken, null))
            .isInstanceOf(BadJWTException.class);
    }

    @ParameterizedTest
    @MethodSource("providerIssuers")
    @DisplayName("All providers should reject tokens from wrong issuer")
    void shouldRejectWrongIssuerAcrossAllProviders(String issuer) throws Exception {
        setupOidcWireMock(issuer, 0);
        String wrongIssuerToken = createSignedTokenWithIssuer(rsaKey, "user-123",
            new Date(System.currentTimeMillis() + 10000), "http://evil.com/realms/fake");

        assertThatThrownBy(() -> jwtProcessor.getProcessor().process(wrongIssuerToken, null))
            .isInstanceOf(BadJWTException.class)
            .hasMessageContaining("JWT iss claim");
    }

    static Stream<String> providerIssuers() {
        return Stream.of(
            "http://localhost:9999/auth/realms/keycloak",
            "http://localhost:9999/application/o/booklore",
            "http://localhost:9999" // Authelia
        );
    }

    private void setupMockSettings(String issuerUri) {
        when(appSettingService.getAppSettings()).thenReturn(createAppSettings(issuerUri, DEFAULT_CLIENT_ID));
    }

    private void setupOidcWireMock(String issuer, int delayMs) {
        setupOidcWireMock(issuer, issuer, issuer + "/protocol/openid-connect/certs", delayMs, List.of());
    }

    private void setupOidcWireMock(String configuredIssuer,
                                   String discoveryIssuer,
                                   String jwksUri,
                                   int delayMs,
                                   List<String> advertisedAlgorithms) {
        setupMockSettings(configuredIssuer);

        String normalizedIssuer = configuredIssuer != null && configuredIssuer.endsWith("/")
                ? configuredIssuer.substring(0, configuredIssuer.length() - 1)
                : configuredIssuer;

        String discoveryPath = URI.create(normalizedIssuer + "/.well-known/openid-configuration").getPath();
        String jwksPath = URI.create(jwksUri).getPath();

        String issuerJson = discoveryIssuer != null ? "\"issuer\": \"%s\",".formatted(discoveryIssuer) : "";
        String algorithmsJson = advertisedAlgorithms == null || advertisedAlgorithms.isEmpty() ? "" :
                ", \"id_token_signing_alg_values_supported\": [" + advertisedAlgorithms.stream()
                        .map(alg -> "\"" + alg + "\"")
                        .collect(Collectors.joining(", ")) + "]";

        stubFor(get(urlEqualTo(discoveryPath))
                .willReturn(okJson("""
                    {
                        %s
                        "jwks_uri": "%s"%s
                    }
                """.formatted(issuerJson, jwksUri, algorithmsJson))));

        stubFor(get(urlEqualTo(jwksPath))
                .willReturn(okJson(new JWKSet(rsaKey).toPublicJWKSet().toString())
                .withFixedDelay(delayMs)));
    }

    private String createSignedToken(RSAKey signingKey, String subject, Date expiration) {
        try {
            JWSSigner signer = new RSASSASigner(signingKey);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer("http://localhost:9999/auth/realms/booklore") // Must match Mock
                    .audience(DEFAULT_CLIENT_ID)
                    .expirationTime(expiration)
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claims);

            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign test token", e);
        }
    }

    private String createSignedToken(RSAKey signingKey, String subject, Date expiration, String audience) {
        try {
            JWSSigner signer = new RSASSASigner(signingKey);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer("http://localhost:9999/auth/realms/booklore")
                    .audience(audience)
                    .expirationTime(expiration)
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claims);

            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign test token", e);
        }
    }

    private String createSignedTokenWithAudience(RSAKey signingKey, String subject, Date expiration, String audience) {
        try {
            JWSSigner signer = new RSASSASigner(signingKey);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer("http://localhost:9999/auth/realms/booklore")
                    .audience(audience)
                    .expirationTime(expiration)
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claims);

            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign test token", e);
        }
    }

    private String createSignedTokenHS256(String subject, Date expiration) {
        try {
            // Create a simple shared secret for HS256 (not secure, just for testing)
            String sharedSecret = "test-secret-key-for-hs256-testing-only";
            JWSSigner signer = new com.nimbusds.jose.crypto.MACSigner(sharedSecret);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer("http://localhost:9999/auth/realms/booklore")
                    .audience(DEFAULT_CLIENT_ID)
                    .expirationTime(expiration)
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.HS256).build(),
                    claims);

            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign HS256 test token", e);
        }
    }

    private String createSignedTokenWithIssuer(RSAKey signingKey, String subject, Date expiration, String issuer) {
        try {
            JWSSigner signer = new RSASSASigner(signingKey);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .audience(DEFAULT_CLIENT_ID)
                    .expirationTime(expiration)
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claims);

            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign test token with custom issuer", e);
        }
    }

    private AppSettings createAppSettings(String issuerUri, String clientId) {
        OidcProviderDetails providerDetails = new OidcProviderDetails();
        providerDetails.setIssuerUri(issuerUri);
        providerDetails.setClientId(clientId);

        return AppSettings.builder()
                .oidcProviderDetails(providerDetails)
                .autoBookSearch(false)
                .similarBookRecommendation(false)
                .opdsServerEnabled(false)
                .remoteAuthEnabled(false)
                .bookDeletionEnabled(false)
                .metadataDownloadOnBookdrop(false)
                .oidcEnabled(true)
                .build();
    }

    @Test
    @DisplayName("Should fail gracefully on malformed discovery JSON")
    void shouldRejectMalformedDiscoveryDocument() {
        setupMockSettings("http://localhost:9999/auth/realms/broken");

        stubFor(get("/auth/realms/broken/.well-known/openid-configuration")
            .willReturn(okJson("{ invalid json {{{")));

        assertThatThrownBy(() -> jwtProcessor.getProcessor())
            .hasRootCauseInstanceOf(com.fasterxml.jackson.core.JsonParseException.class);
    }
}
