package com.adityachandel.booklore.config.security.filter;

import com.adityachandel.booklore.config.security.JwtUtils;
import com.adityachandel.booklore.config.security.service.DynamicOidcJwtProcessor;
import com.adityachandel.booklore.config.security.userdetails.UserAuthenticationDetails;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * JWT filter for audiobook streaming endpoints that supports both:
 * 1. Authorization header (Bearer token) - for fetch() requests
 * 2. Query parameter (token) - for HTML5 audio element compatibility
 */
@Component
@AllArgsConstructor
public class AudiobookStreamingJwtFilter extends OncePerRequestFilter {

    private static final Pattern AUDIOBOOK_STREAMING_ENDPOINT_PATTERN =
            Pattern.compile("/api/v1/audiobook/\\d+/(stream|track/\\d+/stream|cover).*");

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final AppSettingService appSettingService;
    private final DynamicOidcJwtProcessor dynamicOidcJwtProcessor;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !AUDIOBOOK_STREAMING_ENDPOINT_PATTERN.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Try Authorization header first, then fall back to query parameter
        String token = extractTokenFromHeader(request);
        if (token == null) {
            token = request.getParameter("token");
        }

        if (token == null || token.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authentication token");
            return;
        }

        try {
            if (jwtUtils.validateToken(token)) {
                authenticateLocalUser(token, request);
            } else if (appSettingService.getAppSettings().isOidcEnabled()) {
                authenticateOidcUser(token, request);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }
        } catch (Exception ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed: " + ex.getMessage());
            return;
        }

        chain.doFilter(request, response);
    }

    private String extractTokenFromHeader(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return (bearer != null && bearer.startsWith("Bearer ")) ? bearer.substring(7) : null;
    }

    private void authenticateLocalUser(String token, HttpServletRequest request) {
        Long userId = jwtUtils.extractUserId(token);
        BookLoreUserEntity entity = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, null);
        authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void authenticateOidcUser(String token, HttpServletRequest request) throws Exception {
        var processor = dynamicOidcJwtProcessor.getProcessor();
        var claimsSet = processor.process(token, null);

        if (claimsSet.getExpirationTime() == null ||
            claimsSet.getExpirationTime().toInstant().isBefore(Instant.now())) {
            throw new RuntimeException("OIDC token expired or invalid");
        }

        OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
        OidcProviderDetails.ClaimMapping claimMapping = providerDetails.getClaimMapping();
        String username = claimsSet.getStringClaim(claimMapping.getUsername());
        BookLoreUserEntity entity = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("OIDC user not found: " + username));
        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, null);
        authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
