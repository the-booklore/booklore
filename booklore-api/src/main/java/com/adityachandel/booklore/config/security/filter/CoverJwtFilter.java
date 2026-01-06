package com.adityachandel.booklore.config.security.filter;

import com.adityachandel.booklore.config.security.JwtUtils;
import com.adityachandel.booklore.config.security.service.DynamicOidcJwtProcessor;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
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