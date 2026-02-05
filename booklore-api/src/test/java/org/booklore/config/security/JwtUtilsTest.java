package org.booklore.config.security;

import org.booklore.config.security.service.OidcProperties;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.service.security.JwtSecretService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtUtilsTest {

    @Mock
    private JwtSecretService jwtSecretService;

    @Mock
    private OidcProperties oidcProperties;

    @InjectMocks
    private JwtUtils jwtUtils;

    @Test
    void generateRefreshToken_ShouldBeUnique_InSameSecond() {
        when(jwtSecretService.getSecret()).thenReturn("verysecretkeythatislongenoughforhs256encryption");
        when(oidcProperties.jwt()).thenReturn(new org.booklore.config.security.service.OidcProperties.Jwt(Duration.ofSeconds(60), false, 10000, null));
        
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setName("Test User");
        user.setDefaultPassword(false);

        String token1 = jwtUtils.generateRefreshToken(user);
        String token2 = jwtUtils.generateRefreshToken(user);

        assertNotEquals(token1, token2, "Refresh tokens should be unique due to random JTI");
        
        io.jsonwebtoken.Claims claims1 = jwtUtils.extractClaims(token1);
        io.jsonwebtoken.Claims claims2 = jwtUtils.extractClaims(token2);
        
        assertNotEquals(claims1.getId(), claims2.getId(), "JTI claims should be different");
    }
}
