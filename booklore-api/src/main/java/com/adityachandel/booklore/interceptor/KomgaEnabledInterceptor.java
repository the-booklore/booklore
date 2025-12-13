package com.adityachandel.booklore.interceptor;

import com.adityachandel.booklore.service.appsettings.AppSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class KomgaEnabledInterceptor implements HandlerInterceptor {

    private final AppSettingService appSettingService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/") || uri.startsWith("/api/v2/")) {
            // Check if it's a Komga API endpoint (not OPDS, auth, or other existing endpoints)
            if (isKomgaEndpoint(uri)) {
                if (!appSettingService.getAppSettings().isKomgaApiEnabled()) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Komga API is disabled.");
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isKomgaEndpoint(String uri) {
        // Exclude existing endpoints
        if (uri.startsWith("/api/v1/opds") || uri.startsWith("/api/v2/opds")) {
            return false;
        }
        if (uri.startsWith("/api/v1/auth") || uri.startsWith("/api/v1/public-settings") || uri.startsWith("/api/v1/setup")) {
            return false;
        }
        
        // Include Komga API endpoints
        return uri.matches("^/api/v[12]/(libraries|series|books|authors|publishers|genres|languages|tags|age-ratings|users|collections|readlists|history|filesystem|settings|releases|announcements|tasks|page-hashes|client-settings|syncpoints|claim|oauth2|fonts|transient-books)(/.*)?$");
    }
}
