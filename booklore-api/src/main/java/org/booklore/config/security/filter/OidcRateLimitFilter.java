package org.booklore.config.security.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.OidcProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Slf4j
@Component
public class OidcRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|(2[0-4]|1\\d|[1-9])?\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9])?\\d)$");
    
    private static final Set<String> RATE_LIMITED_ENDPOINTS = Set.of(
            "/api/v1/auth/oidc/token",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh"
    );
    private static final Pattern UNKOWN = Pattern.compile("[^a-zA-Z0-9.:]");

    private final Cache<String, AtomicInteger> requestCounts;
    private final OidcProperties oidcProperties;

    public OidcRateLimitFilter(OidcProperties oidcProperties) {
        this.oidcProperties = oidcProperties;
        this.requestCounts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(100_000) // Prevent memory exhaustion attacks
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        
        if (RATE_LIMITED_ENDPOINTS.contains(requestUri)) {
            if (!oidcProperties.jwks().rateLimitEnabled()) {
                filterChain.doFilter(request, response);
                return;
            }
            
            String clientIp = getClientIp(request);
            AtomicInteger count = requestCounts.get(clientIp, k -> new AtomicInteger(0));
            
            int limit = oidcProperties.jwks().rateLimitRequestsPerMinute();
            int currentCount = count.incrementAndGet();
            
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - currentCount)));
            
            if (currentCount > limit) { 
                String maskedIp = maskIp(clientIp);
                log.warn("OIDC Token Endpoint Rate limit exceeded for IP: {} on endpoint: {}", maskedIp, requestUri);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.setHeader("Retry-After", "60"); // RFC 7231 compliant
                response.getWriter().write("{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded. Please try again in 60 seconds.\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty() && isValidIp(realIp)) {
            return realIp.trim();
        }
        
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            String clientIp = xfHeader.split(",")[0].trim();
            if (isValidIp(clientIp)) {
                return clientIp;
            }
        }
        
        return request.getRemoteAddr();
    }
    

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty() || ip.length() > 45) { // Max IPv6 length
            return false;
        }
        if (IP_PATTERN.matcher(ip).matches()) {
            return true;
        }
        return ip.contains(":") && !ip.contains(" ");
    }

    private String maskIp(String ip) {
        if (ip == null) return "unknown";
        return UNKOWN.matcher(ip).replaceAll("").substring(0, Math.min(ip.length(), 45));
    }
}
