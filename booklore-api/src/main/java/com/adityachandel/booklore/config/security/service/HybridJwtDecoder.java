package com.adityachandel.booklore.config.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.BadJwtException;

import java.time.Instant;
import java.util.Base64;

/**
 * Hybrid JWT decoder that supports both OIDC tokens (RS256/RS384/RS512) and internal tokens (HS256).
 * This allows the system to accept both Authelia/OIDC authentication and legacy internal JWT tokens
 * for backward compatibility.
 * <p>
 * Algorithm detection is performed by inspecting the JWT header without full validation.
 * - Tokens with alg=HS256 are routed to the internal decoder (symmetric key)
 * - All other tokens are routed to the OIDC decoder (asymmetric JWKS validation)
 * <p>
 * Token expiration is pre-checked to provide clear error messages without unnecessary
 * logging when expired tokens are sent from browser localStorage.
 */
@Slf4j
@RequiredArgsConstructor
public class HybridJwtDecoder implements JwtDecoder {

    private final JwtDecoder oidcDecoder;
    private final JwtDecoder internalDecoder;

    @Override
    public Jwt decode(String token) throws JwtException {
        TokenInfo tokenInfo = inspectToken(token);
        
        if (tokenInfo.expired()) {
            long secondsExpired = tokenInfo.secondsExpired();
            if ("HS256".equals(tokenInfo.algorithm)) {
                log.debug("Internal JWT token expired {} seconds ago. Client should re-authenticate.", secondsExpired);
                throw new BadJwtException(String.format(
                    "JWT token expired %d seconds ago. Please log in again.", secondsExpired));
            } else {
                log.debug("OIDC JWT token (alg={}) expired {} seconds ago. Client should refresh token.",
                    tokenInfo.algorithm, secondsExpired);
                throw new BadJwtException(String.format(
                    "OIDC JWT token expired %d seconds ago. Please refresh your session.", secondsExpired));
            }
        }
        
        // Detect algorithm by inspecting JWT header
        String algorithm = tokenInfo.algorithm;
        
        if ("HS256".equals(algorithm)) {
            // Internal Booklore JWT token (local authentication)
            log.debug("Detected HS256 token, using internal decoder");
            try {
                return internalDecoder.decode(token);
            } catch (JwtException e) {
                // Don't log as ERROR - token might be from old session
                log.debug("Internal JWT validation failed: {}", e.getMessage());
                throw new BadJwtException("Internal JWT validation failed: " + e.getMessage());
            }
        } else {
            // OIDC token (RS256, RS384, RS512, ES256, etc.)
            log.debug("Detected {} token, using OIDC decoder", algorithm);
            try {
                return oidcDecoder.decode(token);
            } catch (JwtException e) {
                log.warn("OIDC JWT validation failed for {} token: {}. Check OIDC provider configuration.", 
                    algorithm, e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Inspect the JWT token to extract algorithm and expiration information
     * without performing full cryptographic validation.
     * This allows us to:
     * 1. Route the token to the appropriate decoder
     * 2. Provide clear error messages for expired tokens
     * 3. Avoid unnecessary logging of expected failures (expired tokens from localStorage)
     */
    private TokenInfo inspectToken(String token) {
        try {
            // JWT format: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("Invalid JWT format: expected 3 parts, got {}", parts.length);
                return new TokenInfo("UNKNOWN", null, false, 0);
            }

            // Decode header (base64url without padding)
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            
            // Extract 'alg' field using simple string parsing (avoid Jackson dependency here)
            String algorithm = "UNKNOWN";
            int algIndex = headerJson.indexOf("\"alg\"");
            if (algIndex != -1) {
                int colonIndex = headerJson.indexOf(':', algIndex);
                int valueStart = headerJson.indexOf('"', colonIndex) + 1;
                int valueEnd = headerJson.indexOf('"', valueStart);
                
                if (valueStart > 0 && valueEnd > valueStart) {
                    algorithm = headerJson.substring(valueStart, valueEnd);
                    log.debug("Extracted algorithm from JWT header: {}", algorithm);
                }
            }
            
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            
            Long exp = null;
            int expIndex = payloadJson.indexOf("\"exp\"");
            if (expIndex != -1) {
                int colonIndex = payloadJson.indexOf(':', expIndex);
                int valueStart = colonIndex + 1;
                int valueEnd = payloadJson.indexOf(',', valueStart);
                if (valueEnd == -1) {
                    valueEnd = payloadJson.indexOf('}', valueStart);
                }
                
                if (valueStart > 0 && valueEnd > valueStart) {
                    String expStr = payloadJson.substring(valueStart, valueEnd).trim();
                    try {
                        exp = Long.parseLong(expStr);
                    } catch (NumberFormatException e) {
                        log.debug("Failed to parse exp claim: {}", expStr);
                    }
                }
            }
            
            boolean isExpired = false;
            long secondsExpired = 0;
            if (exp != null) {
                Instant expiration = Instant.ofEpochSecond(exp);
                Instant now = Instant.now();
                isExpired = now.isAfter(expiration);
                if (isExpired) {
                    secondsExpired = now.getEpochSecond() - exp;
                }
            }
            
            return new TokenInfo(algorithm, exp != null ? Instant.ofEpochSecond(exp) : null, isExpired, secondsExpired);
            
        } catch (Exception e) {
            log.debug("Failed to inspect JWT token: {}", e.getMessage());
            return new TokenInfo("UNKNOWN", null, false, 0);
        }
    }

    private record TokenInfo(String algorithm, Instant expiration, boolean expired, long secondsExpired) {
    }
}
