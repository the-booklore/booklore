package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.config.security.service.OidcProperties;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.service.security.JwtSecretService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final JwtSecretService jwtSecretService;
    private final OidcProperties oidcProperties;

    @Getter
    public static final long accessTokenExpirationMs = 1000L * 60 * 60 * 10;  // 10 hours
    @Getter
    public static final long refreshTokenExpirationMs = 1000L * 60 * 60 * 24 * 30; // 30 days

    private SecretKey getSigningKey() {
        String secretKey = jwtSecretService.getSecret();
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(BookLoreUserEntity user, boolean isRefreshToken) {
        long expirationTime = isRefreshToken ? refreshTokenExpirationMs : accessTokenExpirationMs;
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("isDefaultPassword", user.isDefaultPassword())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .id(UUID.randomUUID().toString()) // Add JTI to ensure uniqueness
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationTime)))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String generateAccessToken(BookLoreUserEntity user) {
        return generateToken(user, false);
    }

    public String generateRefreshToken(BookLoreUserEntity user) {
        return generateToken(user, true);
    }

    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.debug("Invalid token: {}", e.getMessage());
        }
        return false;
    }

    public Claims extractClaims(String token) {
        long skewSeconds = oidcProperties.jwt().clockSkew().toSeconds();

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .clockSkewSeconds(skewSeconds)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        Object userIdClaim = extractClaims(token).get("userId");
        if (userIdClaim instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("Invalid userId claim type");
    }
}