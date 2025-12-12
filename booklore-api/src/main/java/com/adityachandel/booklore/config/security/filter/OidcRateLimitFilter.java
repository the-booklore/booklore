package com.adityachandel.booklore.config.security.filter;

import com.adityachandel.booklore.config.security.service.OidcProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class OidcRateLimitFilter extends OncePerRequestFilter {

    private final Cache<String, AtomicInteger> requestCounts;
    private final OidcProperties oidcProperties;

    public OidcRateLimitFilter(OidcProperties oidcProperties) {
        this.oidcProperties = oidcProperties;
        this.requestCounts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().equals("/api/v1/auth/oidc/token")) {
            if (!oidcProperties.jwks().rateLimitEnabled()) {
                filterChain.doFilter(request, response);
                return;
            }
            
            String clientIp = getClientIp(request);
            AtomicInteger count = requestCounts.get(clientIp, k -> new AtomicInteger(0));
            
            int limit = oidcProperties.jwks().rateLimitRequestsPerMinute();
            if (count.incrementAndGet() > limit) { 
                log.warn("OIDC Token Endpoint Rate limit exceeded for IP: {}", clientIp);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded. Please try again later.\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
