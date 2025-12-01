package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.config.security.service.SimpleRoleConverter;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OidcLogicTest {

    @Mock private UserProvisioningService userProvisioningService;
    @Mock private AppSettingService appSettingService;
    @Mock private SimpleRoleConverter roleConverter;
    @Mock private BookLoreUserTransformer bookLoreUserTransformer;

    @InjectMocks
    private OidcJwtAuthenticationConverter converter;

    @Test
    void shouldConvertTokenToUser() {
        // Setup
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", "john")
                .claim("email", "john@example.com")
                .claim("name", "John Doe")
                .build();

        AppSettings appSettings = new AppSettings();
        OidcProviderDetails providerDetails = new OidcProviderDetails();
        OidcProviderDetails.ClaimMapping claimMapping = new OidcProviderDetails.ClaimMapping();
        claimMapping.setUsername("preferred_username");
        providerDetails.setClaimMapping(claimMapping);
        appSettings.setOidcProviderDetails(providerDetails);
        
        OidcAutoProvisionDetails autoProvisionDetails = new OidcAutoProvisionDetails();
        appSettings.setOidcAutoProvisionDetails(autoProvisionDetails);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(userProvisioningService.provisionOidcUser(eq("john"), eq("john@example.com"), eq("John Doe"), any()))
                .thenReturn(new BookLoreUserEntity());
        when(bookLoreUserTransformer.toDTO(any())).thenReturn(new BookLoreUser());

        // Run
        var result = converter.convert(jwt);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getPrincipal()).isInstanceOf(BookLoreUser.class);
        verify(userProvisioningService).provisionOidcUser(eq("john"), eq("john@example.com"), eq("John Doe"), any());
    }

    @Test
    void shouldFallbackToSubWhenUsernameClaimMissing() {
        // Setup
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                // No preferred_username
                .subject("user-123")
                .build();

        AppSettings appSettings = new AppSettings();
        OidcProviderDetails providerDetails = new OidcProviderDetails();
        // Default claim mapping uses preferred_username
        appSettings.setOidcProviderDetails(providerDetails);
        
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(userProvisioningService.provisionOidcUser(eq("user-123"), any(), any(), any()))
                .thenReturn(new BookLoreUserEntity());
        when(bookLoreUserTransformer.toDTO(any())).thenReturn(new BookLoreUser());

        // Run
        var result = converter.convert(jwt);

        // Verify
        assertThat(result).isNotNull();
        verify(userProvisioningService).provisionOidcUser(eq("user-123"), any(), any(), any());
    }
}