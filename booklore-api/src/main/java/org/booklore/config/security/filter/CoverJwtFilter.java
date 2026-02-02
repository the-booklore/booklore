package org.booklore.config.security.filter;

import org.booklore.config.security.JwtUtils;
import org.booklore.config.security.service.DynamicOidcJwtProcessor;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class CoverJwtFilter extends AbstractQueryParameterJwtFilter {

    public CoverJwtFilter(
            JwtUtils jwtUtils,
            UserRepository userRepository,
            BookLoreUserTransformer bookLoreUserTransformer,
            AppSettingService appSettingService,
            DynamicOidcJwtProcessor dynamicOidcJwtProcessor) {
        super(jwtUtils, userRepository, bookLoreUserTransformer, appSettingService, dynamicOidcJwtProcessor);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/media/");
    }
}