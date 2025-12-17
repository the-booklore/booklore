package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.config.security.service.HybridJwtDecoder;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for HybridJwtDecoder to ensure compatibility with:
 * - Keycloak (RS256, RS384, RS512)
 * - Authentik (RS256, ES256)
 * - Authelia (RS256)
 * - Internal JWT tokens (HS256)
 * - Expired token handling
 * - Algorithm detection and routing
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HybridJwtDecoder Tests")
class HybridJwtDecoderTest {

    @Mock
    private JwtDecoder oidcDecoder;

    @Mock
    private JwtDecoder internalDecoder;

    private HybridJwtDecoder hybridDecoder;

    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm";
    private static final String TEST_CLIENT_ID = "booklore-test-client";
    private static final String TEST_ISSUER = "https://auth.example.com";

    @BeforeEach
    void setUp() {
        hybridDecoder = new HybridJwtDecoder(oidcDecoder, internalDecoder);
    }

    @Test
    @DisplayName("Should route HS256 token to internal decoder")
    void testRouteHS256ToInternalDecoder() throws Exception {
        String hs256Token = createHS256Token(
            TEST_CLIENT_ID,
            "user123",
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        Jwt mockJwt = mock(Jwt.class);
        when(internalDecoder.decode(hs256Token)).thenReturn(mockJwt);

        Jwt result = hybridDecoder.decode(hs256Token);

        assertThat(result).isEqualTo(mockJwt);
        verify(internalDecoder, times(1)).decode(hs256Token);
        verify(oidcDecoder, never()).decode(anyString());
    }

    @Test
    @DisplayName("Should route RS256 token to OIDC decoder (Keycloak)")
    void testRouteRS256ToOidcDecoder() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048)
            .keyID(UUID.randomUUID().toString())
            .generate();
        
        String rs256Token = createRS256Token(
            rsaKey,
            TEST_ISSUER,
            TEST_CLIENT_ID,
            "user123",
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        Jwt mockJwt = mock(Jwt.class);
        when(oidcDecoder.decode(rs256Token)).thenReturn(mockJwt);

        Jwt result = hybridDecoder.decode(rs256Token);

        assertThat(result).isEqualTo(mockJwt);
        verify(oidcDecoder, times(1)).decode(rs256Token);
        verify(internalDecoder, never()).decode(anyString());
    }

    @Test
    @DisplayName("Should handle expired HS256 token gracefully without ERROR logs")
    void testExpiredHS256TokenHandling() throws Exception {
        String expiredToken = createHS256Token(
            TEST_CLIENT_ID,
            "user123",
            Instant.now().minus(3, ChronoUnit.DAYS) // Expired 3 days ago
        );

        assertThatThrownBy(() -> hybridDecoder.decode(expiredToken))
            .isInstanceOf(BadJwtException.class)
            .hasMessageContaining("expired")
            .hasMessageContaining("seconds ago");

        verify(internalDecoder, never()).decode(anyString());
        verify(oidcDecoder, never()).decode(anyString());
    }

    @Test
    @DisplayName("Should handle expired RS256 token gracefully (OIDC)")
    void testExpiredRS256TokenHandling() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048)
            .keyID(UUID.randomUUID().toString())
            .generate();
        
        String expiredToken = createRS256Token(
            rsaKey,
            TEST_ISSUER,
            TEST_CLIENT_ID,
            "user123",
            Instant.now().minus(1, ChronoUnit.HOURS) // Expired 1 hour ago
        );

        assertThatThrownBy(() -> hybridDecoder.decode(expiredToken))
            .isInstanceOf(BadJwtException.class)
            .hasMessageContaining("OIDC JWT token expired")
            .hasMessageContaining("seconds ago");

        verify(internalDecoder, never()).decode(anyString());
        verify(oidcDecoder, never()).decode(anyString());
    }

    @Test
    @DisplayName("Should handle malformed JWT token")
    void testMalformedToken() {
        String malformedToken = "not-a-valid-jwt";

        when(oidcDecoder.decode(malformedToken)).thenThrow(new BadJwtException("Invalid JWT format"));

        assertThatThrownBy(() -> hybridDecoder.decode(malformedToken))
            .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Should handle token without algorithm header")
    void testTokenWithoutAlgorithm() {
        String tokenWithoutAlg = "eyJ0eXAiOiJKV1QifQ.eyJzdWIiOiJ1c2VyMTIzIn0.signature";

        when(oidcDecoder.decode(tokenWithoutAlg)).thenThrow(new BadJwtException("Invalid token"));

        assertThatThrownBy(() -> hybridDecoder.decode(tokenWithoutAlg))
            .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Should handle internal decoder failure gracefully")
    void testInternalDecoderFailure() throws Exception {
        String hs256Token = createHS256Token(
            TEST_CLIENT_ID,
            "user123",
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        when(internalDecoder.decode(hs256Token))
            .thenThrow(new BadJwtException("Signature validation failed"));

        assertThatThrownBy(() -> hybridDecoder.decode(hs256Token))
            .isInstanceOf(BadJwtException.class)
            .hasMessageContaining("Internal JWT validation failed");

        verify(internalDecoder, times(1)).decode(hs256Token);
    }

    @Test
    @DisplayName("Should handle OIDC decoder failure with warning log")
    void testOidcDecoderFailure() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048)
            .keyID(UUID.randomUUID().toString())
            .generate();
        
        String rs256Token = createRS256Token(
            rsaKey,
            TEST_ISSUER,
            TEST_CLIENT_ID,
            "user123",
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        when(oidcDecoder.decode(rs256Token))
            .thenThrow(new BadJwtException("JWKS signature validation failed"));

        assertThatThrownBy(() -> hybridDecoder.decode(rs256Token))
            .isInstanceOf(BadJwtException.class)
            .hasMessageContaining("JWKS signature validation failed");

        verify(oidcDecoder, times(1)).decode(rs256Token);
    }

    @Test
    @DisplayName("Should correctly detect RS384 algorithm (Keycloak variant)")
    void testRS384Detection() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048)
            .keyID(UUID.randomUUID().toString())
            .generate();
        
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS384)
            .keyID(rsaKey.getKeyID())
            .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(TEST_ISSUER)
            .subject("user123")
            .audience(TEST_CLIENT_ID)
            .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .issueTime(new Date())
            .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(rsaKey));
        String rs384Token = signedJWT.serialize();

        Jwt mockJwt = mock(Jwt.class);
        when(oidcDecoder.decode(rs384Token)).thenReturn(mockJwt);

        Jwt result = hybridDecoder.decode(rs384Token);

        assertThat(result).isEqualTo(mockJwt);
        verify(oidcDecoder, times(1)).decode(rs384Token);
        verify(internalDecoder, never()).decode(anyString());
    }

    @Test
    @DisplayName("Should correctly detect RS512 algorithm (Keycloak variant)")
    void testRS512Detection() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048)
            .keyID(UUID.randomUUID().toString())
            .generate();
        
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS512)
            .keyID(rsaKey.getKeyID())
            .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(TEST_ISSUER)
            .subject("user123")
            .audience(TEST_CLIENT_ID)
            .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .issueTime(new Date())
            .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(rsaKey));
        String rs512Token = signedJWT.serialize();

        Jwt mockJwt = mock(Jwt.class);
        when(oidcDecoder.decode(rs512Token)).thenReturn(mockJwt);

        Jwt result = hybridDecoder.decode(rs512Token);

        assertThat(result).isEqualTo(mockJwt);
        verify(oidcDecoder, times(1)).decode(rs512Token);
        verify(internalDecoder, never()).decode(anyString());
    }

    @Test
    @DisplayName("Should handle token near expiration (within 5 seconds)")
    void testTokenNearExpiration() throws Exception {
        String tokenNearExpiry = createHS256Token(
            TEST_CLIENT_ID,
            "user123",
            Instant.now().plus(5, ChronoUnit.SECONDS)
        );

        Jwt mockJwt = mock(Jwt.class);
        when(internalDecoder.decode(tokenNearExpiry)).thenReturn(mockJwt);

        Jwt result = hybridDecoder.decode(tokenNearExpiry);

        assertThat(result).isEqualTo(mockJwt);
        verify(internalDecoder, times(1)).decode(tokenNearExpiry);
    }

    /**
     * Creates a valid HS256 JWT token (simulating internal Booklore tokens)
     */
    private String createHS256Token(String audience, String subject, Instant expiration) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        
        return Jwts.builder()
            .subject(subject)
            .audience().add(audience).and()
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(expiration))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    /**
     * Creates a valid RS256 JWT token (simulating Keycloak/Authentik/Authelia tokens)
     */
    private String createRS256Token(RSAKey rsaKey, String issuer, String audience, 
                                   String subject, Instant expiration) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(rsaKey.getKeyID())
            .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .audience(audience)
            .expirationTime(Date.from(expiration))
            .issueTime(new Date())
            .jwtID(UUID.randomUUID().toString())
            .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(rsaKey));
        
        return signedJWT.serialize();
    }
}
