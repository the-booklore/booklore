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
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
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
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
        String usernameClaim = "preferred_username";
        String emailClaim = "email";
        String nameClaim = "name";

        if (providerDetails != null && providerDetails.getClaimMapping() != null) {
            OidcProviderDetails.ClaimMapping mapping = providerDetails.getClaimMapping();
            if (mapping.getUsername() != null && !mapping.getUsername().isEmpty()) {
                usernameClaim = mapping.getUsername();
            }
            if (mapping.getEmail() != null && !mapping.getEmail().isEmpty()) {
                emailClaim = mapping.getEmail();
            }
            if (mapping.getName() != null && !mapping.getName().isEmpty()) {
                nameClaim = mapping.getName();
            }
        }

        String username = jwt.getClaimAsString(usernameClaim);
        if (username == null || username.isEmpty()) {
            username = jwt.getSubject();
            logger.debug("Username claim '{}' missing/empty. Falling back to 'sub': {}", usernameClaim, username);
        }
        
        String email = jwt.getClaimAsString(emailClaim);
        String name = jwt.getClaimAsString(nameClaim);

        logger.info("🔐 OIDC Authentication - JWT Claims Analysis:");
        logger.info("   └─ Username claim '{}' = '{}'", usernameClaim, username);
        logger.info("   └─ Email claim '{}' = '{}'", emailClaim, email);
        logger.info("   └─ Name claim '{}' = '{}'", nameClaim, name);
        logger.info("   └─ Subject (sub) = '{}'", jwt.getSubject());
        logger.info("   └─ All claims: {}", jwt.getClaims().keySet());
        
        logger.debug("OidcJwtAuthenticationConverter.convert() called for username: {}", username);

        OidcAutoProvisionDetails provisionDetails = appSettingService.getAppSettings().getOidcAutoProvisionDetails();

        BookLoreUserEntity userEntity = userProvisioningService.provisionOidcUser(username, email, name, provisionDetails);

        BookLoreUser userDto = bookLoreUserTransformer.toDTO(userEntity);

        Collection<GrantedAuthority> authorities = roleConverter.convert(jwt);

        return new UsernamePasswordAuthenticationToken(userDto, jwt, authorities);
    }
}