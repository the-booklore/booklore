package org.booklore.config.security.interceptor;

import org.booklore.config.security.JwtUtils;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.user.UserProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageChannel;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private AppSettingService appSettingService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookLoreUserTransformer bookLoreUserTransformer;
    @Mock
    private UserProvisioningService userProvisioningService;

    @Mock
    private MessageChannel channel;

    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(
                jwtUtils,
                appSettingService,
                userRepository,
                bookLoreUserTransformer,
                userProvisioningService
        );
    }

    @Test
    void authenticateToken_OidcMismatch_ShouldReturnNull() {
        String token = "oidc.token.value";

        AppSettings appSettings = new AppSettings();
        appSettings.setOidcEnabled(true);
        OidcProviderDetails providerDetails = new OidcProviderDetails();
        OidcProviderDetails.ClaimMapping claimMapping = new OidcProviderDetails.ClaimMapping();
        claimMapping.setUsername("sub");
        claimMapping.setEmail("email");
        claimMapping.setName("name");
        claimMapping.setGroups("groups");
        providerDetails.setClaimMapping(claimMapping);
        appSettings.setOidcProviderDetails(providerDetails);

        when(jwtUtils.validateToken(token)).thenReturn(false); // Not a local JWT
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        lenient().when(userRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(bookLoreUserTransformer.toDTO(any())).thenReturn(BookLoreUser.builder().build());

        when(userProvisioningService.provisionUserFromOidcToken(token))
                .thenReturn(null);

        Authentication result = interceptor.authenticateToken(token);

        // Assert
        assertThat(result).isNull();
        verify(userProvisioningService).provisionUserFromOidcToken(token);
    }

    @Test
    void authenticateToken_OidcSuccess_ShouldReturnAuthentication() {
        String token = "oidc.token.value";

        AppSettings appSettings = new AppSettings();
        appSettings.setOidcEnabled(true);
        when(jwtUtils.validateToken(token)).thenReturn(false);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        lenient().when(userRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(bookLoreUserTransformer.toDTO(any())).thenReturn(BookLoreUser.builder().build());

        BookLoreUser userDto = BookLoreUser.builder()
                .id(1L)
                .username("newuser")
                .email("newuser@example.com")
                .name("New User")
                .build();
        when(userProvisioningService.provisionUserFromOidcToken(token)).thenReturn(userDto);

        Authentication result = interceptor.authenticateToken(token);

        assertThat(result).isNotNull();
        assertThat(result.getPrincipal()).isEqualTo(userDto);
        assertThat(result).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    }


    @Test
    void authenticateToken_OidcDisabled_ShouldReturnNull() {
        String token = "some.token.value";

        AppSettings appSettings = new AppSettings();
        appSettings.setOidcEnabled(false);

        when(jwtUtils.validateToken(token)).thenReturn(false);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        lenient().when(userRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(bookLoreUserTransformer.toDTO(any())).thenReturn(BookLoreUser.builder().build());

        Authentication result = interceptor.authenticateToken(token);

        assertThat(result).isNull();

        verify(userProvisioningService, never()).provisionUserFromOidcToken(anyString());
    }
}
