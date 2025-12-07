package com.adityachandel.booklore.config.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Base64;

/**
 * Hybrid JWT decoder that supports both OIDC tokens (RS256/RS384/RS512) and internal tokens (HS256).
 * This allows the system to accept both Authelia/OIDC authentication and legacy internal JWT tokens
 * for backward compatibility.
 * <p>
 * Algorithm detection is performed by inspecting the JWT header without full validation.
 * - Tokens with alg=HS256 are routed to the internal decoder (symmetric key)
 * - All other tokens are routed to the OIDC decoder (asymmetric JWKS validation)
 */
@Slf4j
@RequiredArgsConstructor
public class HybridJwtDecoder implements JwtDecoder {

    private final JwtDecoder oidcDecoder;
    private final JwtDecoder internalDecoder;

    @Override
    public Jwt decode(String token) throws JwtException {
        // Detect algorithm by inspecting JWT header
        String algorithm = extractAlgorithmFromToken(token);
        
        if ("HS256".equals(algorithm)) {
            // Internal Booklore JWT token (local authentication)
            log.debug("Detected HS256 token, using internal decoder");
            try {
                return internalDecoder.decode(token);
            } catch (JwtException e) {
                log.error("Internal JWT validation failed: {}", e.getMessage());
                throw e;
            }
        } else {
            // OIDC token (RS256, RS384, RS512, ES256, etc.)
            log.debug("Detected {} token, using OIDC decoder", algorithm);
            try {
                return oidcDecoder.decode(token);
            } catch (JwtException e) {
                log.error("OIDC JWT validation failed for {} token: {}", algorithm, e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Extract the 'alg' claim from the JWT header without full validation.
     * This allows us to route the token to the appropriate decoder.
     */
    private String extractAlgorithmFromToken(String token) {
        try {
            // JWT format: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                log.warn("Invalid JWT format: expected 3 parts, got {}", parts.length);
                return "UNKNOWN";
            }

            // Decode header (base64url without padding)
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            
            // Extract 'alg' field using simple string parsing (avoid Jackson dependency here)
            int algIndex = headerJson.indexOf("\"alg\"");
            if (algIndex == -1) {
                log.warn("JWT header missing 'alg' field: {}", headerJson);
                return "UNKNOWN";
            }
            
            int colonIndex = headerJson.indexOf(":", algIndex);
            int valueStart = headerJson.indexOf("\"", colonIndex) + 1;
            int valueEnd = headerJson.indexOf("\"", valueStart);
            
            if (valueStart > 0 && valueEnd > valueStart) {
                String alg = headerJson.substring(valueStart, valueEnd);
                log.debug("Extracted algorithm from JWT header: {}", alg);
                return alg;
            }
            
            log.warn("Failed to parse 'alg' value from JWT header: {}", headerJson);
            return "UNKNOWN";
        } catch (Exception e) {
            log.error("Failed to extract algorithm from JWT: {}", e.getMessage());
            return "UNKNOWN";
        }
    }
}
