package org.booklore.config.security.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.JwtUtils;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.user.UserProvisioningService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final AppSettingService appSettingService;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final UserProvisioningService userProvisioningService;

    @Override
    public Message<?> preSend(@Nullable Message<?> message, @Nullable MessageChannel channel) {
        if (message == null) {
            return null;
        }
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

    Authentication authenticateToken(String token) {
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

            if (appSettingService.getAppSettings().isOidcEnabled()) {
                BookLoreUser user = userProvisioningService.provisionUserFromOidcToken(token);
                if (user != null) {
                    return new UsernamePasswordAuthenticationToken(user, null, null);
                }
            }
        } catch (Exception e) {
            log.debug("Token authentication failed: {}", e.getMessage());
        }
        return null;
    }
}