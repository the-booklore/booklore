package org.booklore.service.user;

import com.nimbusds.jwt.JWTClaimsSet;
import org.booklore.config.AppProperties;
import org.booklore.config.security.service.DynamicOidcJwtProcessor;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.UserCreateRequest;
import org.booklore.model.dto.request.InitialUserRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.OidcAutoProvisionDetails;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private UserDefaultsService userDefaultsService;
    @Mock
    private AppProperties appProperties;
    @Mock
    private AppSettingService appSettingService;
    @Mock
    private DynamicOidcJwtProcessor dynamicOidcJwtProcessor;
    @Mock
    private UserPersistenceService userPersistenceService;

    @InjectMocks
    private UserProvisioningService userProvisioningService;

    @Test
    void provisionInternalUser_SetsPermissionChangePassword_True() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        request.setPermissionChangePassword(true);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(userRepository.save(any(BookLoreUserEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        userProvisioningService.provisionInternalUser(request);

        ArgumentCaptor<BookLoreUserEntity> userCaptor = ArgumentCaptor.forClass(BookLoreUserEntity.class);
        verify(userRepository).save(userCaptor.capture());

        assertTrue(userCaptor.getValue().getPermissions().isPermissionChangePassword());
    }

    @Test
    void provisionInternalUser_SetsPermissionChangePassword_False() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        request.setPermissionChangePassword(false);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(userRepository.save(any(BookLoreUserEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        userProvisioningService.provisionInternalUser(request);

        ArgumentCaptor<BookLoreUserEntity> userCaptor = ArgumentCaptor.forClass(BookLoreUserEntity.class);
        verify(userRepository).save(userCaptor.capture());

        assertFalse(userCaptor.getValue().getPermissions().isPermissionChangePassword());
    }

    @Test
    void provisionInitialUser_SetsPermissionChangePassword_True() {
        InitialUserRequest request = new InitialUserRequest();
        request.setUsername("admin");
        request.setPassword("password");

        when(userRepository.save(any(BookLoreUserEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        userProvisioningService.provisionInitialUser(request);

        ArgumentCaptor<BookLoreUserEntity> userCaptor = ArgumentCaptor.forClass(BookLoreUserEntity.class);
        verify(userRepository).save(userCaptor.capture());

        assertTrue(userCaptor.getValue().getPermissions().isPermissionChangePassword());
    }

    @Test
    void provisionUserFromOidcToken_MapsAllPermissionsToDto() throws Exception {
        String token = "valid.token";
        String subject = "sub123";
        String oidcSubject = "issuer:sub123";

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("issuer")
                .build();
        when(dynamicOidcJwtProcessor.process(token)).thenReturn(claims);

        AppSettings appSettings = AppSettings.builder()
                .oidcProviderDetails(new OidcProviderDetails())
                .oidcAutoProvisionDetails(new OidcAutoProvisionDetails())
                .build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("user");
        userEntity.setEmail("email");
        userEntity.setOidcSubject(oidcSubject);
        UserPermissionsEntity permsEntity = new UserPermissionsEntity();

        permsEntity.setPermissionAdmin(true);
        permsEntity.setPermissionUpload(true);
        permsEntity.setPermissionDownload(true);
        permsEntity.setPermissionEditMetadata(true);
        permsEntity.setPermissionManageLibrary(true);
        permsEntity.setPermissionEmailBook(true);
        permsEntity.setPermissionDeleteBook(true);
        permsEntity.setPermissionAccessOpds(true);
        permsEntity.setPermissionSyncKoreader(true);
        permsEntity.setPermissionSyncKobo(true);
        permsEntity.setPermissionManageMetadataConfig(true);
        permsEntity.setPermissionAccessBookdrop(true);
        permsEntity.setPermissionAccessLibraryStats(true);
        permsEntity.setPermissionAccessUserStats(true);
        permsEntity.setPermissionAccessTaskManager(true);
        permsEntity.setPermissionManageGlobalPreferences(true);
        permsEntity.setPermissionManageIcons(true);

        userEntity.setPermissions(permsEntity);

        when(userRepository.findByOidcSubject(oidcSubject)).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any(BookLoreUserEntity.class))).thenReturn(userEntity);

        BookLoreUser result = userProvisioningService.provisionUserFromOidcToken(token);

        assertNotNull(result);
        BookLoreUser.UserPermissions dtoPerms = result.getPermissions();

        assertTrue(dtoPerms.isAdmin());
        assertTrue(dtoPerms.isCanUpload());
        assertTrue(dtoPerms.isCanDownload());
        assertTrue(dtoPerms.isCanEditMetadata());
        assertTrue(dtoPerms.isCanManageLibrary());
        assertTrue(dtoPerms.isCanEmailBook());
        assertTrue(dtoPerms.isCanDeleteBook());
        assertTrue(dtoPerms.isCanAccessOpds());
        assertTrue(dtoPerms.isCanSyncKoReader());
        assertTrue(dtoPerms.isCanSyncKobo());
        assertTrue(dtoPerms.isCanManageMetadataConfig());
        assertTrue(dtoPerms.isCanAccessBookdrop());
        assertTrue(dtoPerms.isCanAccessLibraryStats());
        assertTrue(dtoPerms.isCanAccessUserStats());
        assertTrue(dtoPerms.isCanAccessTaskManager());
        assertTrue(dtoPerms.isCanManageGlobalPreferences());
        assertTrue(dtoPerms.isCanManageIcons());
    }
}