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
        
        if (uri.startsWith("/komga/api/")) {
            boolean komgaEnabled = appSettingService.getAppSettings().isKomgaApiEnabled();
            if (!komgaEnabled) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Komga API is disabled.");
                return false;
            }
        }
        return true;
    }

    private static final String[] KOMGA_ENDPOINT_PREFIXES = {
        "/api/v1/libraries", "/api/v1/series", "/api/v1/books",
        "/api/v1/authors", "/api/v1/publishers", "/api/v1/genres",
        "/api/v1/languages", "/api/v1/tags", "/api/v1/age-ratings",
        "/api/v1/collections", "/api/v1/readlists", "/api/v1/history",
        "/api/v1/filesystem", "/api/v1/settings", "/api/v1/releases",
        "/api/v1/announcements", "/api/v1/tasks", "/api/v1/page-hashes",
        "/api/v1/client-settings", "/api/v1/syncpoints", "/api/v1/claim",
        "/api/v1/oauth2", "/api/v1/fonts", "/api/v1/transient-books",
        "/api/v2/users/me"
    };
}
