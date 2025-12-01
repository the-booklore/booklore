package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.config.security.service.SimpleRoleConverter;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@AllArgsConstructor
public class OidcJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger logger = LoggerFactory.getLogger(OidcJwtAuthenticationConverter.class);

    private final UserProvisioningService userProvisioningService;
    private final AppSettingService appSettingService;
    private final SimpleRoleConverter roleConverter;
    private final BookLoreUserTransformer bookLoreUserTransformer;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // 1. Determine which claim holds the username
        OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
        String usernameClaim = "preferred_username";
        if (providerDetails != null && providerDetails.getClaimMapping() != null) {
            String mapped = providerDetails.getClaimMapping().getUsername();
            if (mapped != null && !mapped.isEmpty()) {
                usernameClaim = mapped;
            }
        }

        // 2. Extract username with fallback
        String username = jwt.getClaimAsString(usernameClaim);
        if (username == null || username.isEmpty()) {
            username = jwt.getSubject();
            logger.debug("Username claim '{}' missing/empty. Falling back to 'sub': {}", usernameClaim, username);
        }
        
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        logger.debug("OidcJwtAuthenticationConverter.convert() called for username: {}", username);

        OidcAutoProvisionDetails provisionDetails = appSettingService.getAppSettings().getOidcAutoProvisionDetails();

        // 3. Ensure user exists in DB (Auto-Provisioning)
        BookLoreUserEntity userEntity = userProvisioningService.provisionOidcUser(username, email, name, provisionDetails);

        // 4. Convert to DTO (Required for SecurityUtil checks)
        BookLoreUser userDto = bookLoreUserTransformer.toDTO(userEntity);

        // 5. Map Roles
        Collection authorities = roleConverter.convert(jwt);

        // 6. Return token with BookLoreUser principal
        return new UsernamePasswordAuthenticationToken(userDto, jwt, authorities);
    }
}