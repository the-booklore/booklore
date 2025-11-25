package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.config.security.service.SimpleRoleConverter;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@AllArgsConstructor
public class OidcJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

    private static final Logger logger = LoggerFactory.getLogger(OidcJwtAuthenticationConverter.class);

    private final UserProvisioningService userProvisioningService;
    private final AppSettingService appSettingService;
    private final SimpleRoleConverter roleConverter;

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        logger.debug("OidcJwtAuthenticationConverter.convert() called for username: {}", username);

        OidcAutoProvisionDetails provisionDetails = appSettingService.getAppSettings().getOidcAutoProvisionDetails();

        BookLoreUserEntity user = userProvisioningService.provisionOidcUser(username, email, name, provisionDetails);

        Collection authorities = roleConverter.convert(jwt);

        return new JwtAuthenticationToken(jwt, authorities, username);
    }
}