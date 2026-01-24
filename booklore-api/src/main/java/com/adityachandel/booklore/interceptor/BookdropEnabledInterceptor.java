package com.adityachandel.booklore.interceptor;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.service.bookdrop.BookdropMonitoringService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class BookdropEnabledInterceptor implements HandlerInterceptor {

    private final BookdropMonitoringService monitoringService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        
        if (uri.startsWith("/api/v1/bookdrop") || uri.startsWith("/api/v1/files/upload/bookdrop")) {
            if (!monitoringService.isBookdropEnabled()) {
                throw ApiError.BOOKDROP_DISABLED.createException();
            }
        }
        return true;
    }
}
