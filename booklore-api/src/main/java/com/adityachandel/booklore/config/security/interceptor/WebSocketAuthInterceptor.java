package com.adityachandel.booklore.config.security.interceptor;

import com.adityachandel.booklore.config.security.JwtUtils;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final JwtDecoder jwtDecoder;
    private final AppSettingService appSettingService;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");

            if (authHeaders == null || authHeaders.isEmpty()) {
                log.debug("WebSocket connection rejected: No Authorization header");
                throw new IllegalArgumentException("Missing Authorization header");
            }

            String token = authHeaders.getFirst().replace("Bearer ", "");
            Authentication auth = authenticateToken(token);

            if (auth == null) {
                log.debug("WebSocket connection rejected: Invalid token");
                throw new IllegalArgumentException("Invalid Authorization token");
            }

            accessor.setUser(auth);
            log.debug("WebSocket authentication successful for user: {}", auth.getName());
        }

        return message;
    }

    private Authentication authenticateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.debug("Token is null or empty");
            return null;
        }
        try {
            if (jwtUtils.validateToken(token)) {
                Long userId = jwtUtils.extractUserId(token);
                BookLoreUserEntity entity = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
                BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
                return new UsernamePasswordAuthenticationToken(user, null, null);
            }

            if (!appSettingService.getAppSettings().isOidcEnabled()) {
                log.debug("OIDC is disabled, skipping OIDC token validation");
                return null;
            }

            Jwt jwt = jwtDecoder.decode(token);
            if (jwt == null) {
                log.debug("OIDC token processing returned null JWT");
                return null;
            }
            OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
            if (providerDetails == null) {
                log.debug("OIDC provider details are null");
                return null;
            }
            if (providerDetails.getClaimMapping() == null) {
                log.debug("OIDC claim mapping is null");
                return null;
            }
            String usernameClaimKey = providerDetails.getClaimMapping().getUsername();
            if (usernameClaimKey == null || usernameClaimKey.trim().isEmpty()) {
                log.debug("Username claim key is null or empty");
                return null;
            }
            String username = jwt.getClaimAsString(usernameClaimKey);
            if (username != null && !username.trim().isEmpty()) {
                BookLoreUserEntity entity = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("OIDC user not found: " + username));
                BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
                return new UsernamePasswordAuthenticationToken(user, null, null);
            }

            log.warn("Username extracted from OIDC claims is null or empty");

        } catch (Exception e) {
            log.debug("Token authentication failed", e);
        }
        return null;
    }
}