package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.config.security.service.SimpleRoleConverter;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import lombok.AllArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@AllArgsConstructor
public class OidcJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

    private final UserProvisioningService userProvisioningService;
    private final AppSettingService appSettingService;
    private final SimpleRoleConverter roleConverter;

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        OidcAutoProvisionDetails provisionDetails = appSettingService.getAppSettings().getOidcAutoProvisionDetails();

        BookLoreUserEntity user = userProvisioningService.provisionOidcUser(username, email, name, provisionDetails);

        Collection authorities = roleConverter.convert(jwt);

        return new JwtAuthenticationToken(jwt, authorities, username);
    }
}