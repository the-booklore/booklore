package org.booklore.service.user;

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

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPersistenceServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private UserDefaultsService userDefaultsService;
    @Mock
    private AppSettingService appSettingService;

    @InjectMocks
    private UserPersistenceService userPersistenceService;

    @Test
    void provisionOidcUserInternal_SetsPermissionChangePassword_True() {
        String username = "oidcuser";
        String email = "user@example.com";
        String subject = "sub123";
        OidcAutoProvisionDetails details = new OidcAutoProvisionDetails();
        details.setDefaultPermissions(Collections.emptyList());

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByOidcSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(BookLoreUserEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        userPersistenceService.provisionOidcUserInternal(subject, username, email, "User Name", null, details);

        ArgumentCaptor<BookLoreUserEntity> userCaptor = ArgumentCaptor.forClass(BookLoreUserEntity.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());

        BookLoreUserEntity savedUser = userCaptor.getValue();
        assertTrue(savedUser.getPermissions().isPermissionChangePassword(), "OIDC users should have permission to change password to set local password for OPDS and backup access");
    }

    @Test
    void provisionOidcUserInternal_SetsAllDefaultPermissions() {
        String username = "oidcuser_defaults";
        String email = "defaults@example.com";
        String subject = "sub_defaults";

        OidcAutoProvisionDetails details = new OidcAutoProvisionDetails();
        details.setDefaultPermissions(List.of(
            "permissionUpload",
            "permissionDownload",
            "permissionEditMetadata",
            "permissionManageLibrary",
            "permissionEmailBook",
            "permissionDeleteBook",
            "permissionAccessOpds",
            "permissionSyncKoreader",
            "permissionSyncKobo",
            "permissionManageMetadataConfig",
            "permissionAccessBookdrop",
            "permissionAccessLibraryStats",
            "permissionAccessUserStats",
            "permissionAccessTaskManager",
            "permissionManageGlobalPreferences",
            "permissionManageIcons"
        ));

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByOidcSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(BookLoreUserEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        userPersistenceService.provisionOidcUserInternal(subject, username, email, "Defaults User", null, details);

        ArgumentCaptor<BookLoreUserEntity> userCaptor = ArgumentCaptor.forClass(BookLoreUserEntity.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());

        UserPermissionsEntity perms = userCaptor.getValue().getPermissions();

        assertTrue(perms.isPermissionUpload());
        assertTrue(perms.isPermissionDownload());
        assertTrue(perms.isPermissionEditMetadata());
        assertTrue(perms.isPermissionManageLibrary());
        assertTrue(perms.isPermissionEmailBook());
        assertTrue(perms.isPermissionDeleteBook());
        assertTrue(perms.isPermissionAccessOpds());
        assertTrue(perms.isPermissionSyncKoreader());
        assertTrue(perms.isPermissionSyncKobo());

        assertTrue(perms.isPermissionManageMetadataConfig());
        assertTrue(perms.isPermissionAccessBookdrop());
        assertTrue(perms.isPermissionAccessLibraryStats());
        assertTrue(perms.isPermissionAccessUserStats());
        assertTrue(perms.isPermissionAccessTaskManager());
        assertTrue(perms.isPermissionManageGlobalPreferences());
        assertTrue(perms.isPermissionManageIcons());
    }

    @Test
    void syncUserPermissionsFromGroups_SyncsAllDetailedPermissions() {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername("syncedUser");
        UserPermissionsEntity perms = new UserPermissionsEntity();
        perms.setUser(user);
        user.setPermissions(perms);

        List<String> groups = List.of("power_users", "metadata_editors");
        OidcAutoProvisionDetails provisionDetails = new OidcAutoProvisionDetails();
        provisionDetails.setSyncPermissions(true);

        OidcProviderDetails providerDetails = new OidcProviderDetails();
        Map<String, List<String>> roleMappings = new HashMap<>();
        roleMappings.put("power_users", List.of(
            "permissionUpload", "permissionDownload", "permissionAccessTaskManager", "permissionManageGlobalPreferences"
        ));
        roleMappings.put("metadata_editors", List.of(
            "permissionEditMetadata", "permissionManageMetadataConfig", "permissionAccessBookdrop"
        ));
        providerDetails.setRoleMappings(roleMappings);

        AppSettings appSettings = AppSettings.builder().oidcProviderDetails(providerDetails).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        userPersistenceService.syncUserPermissionsFromGroups(user, groups, provisionDetails);

        verify(userRepository).save(user);

        assertTrue(perms.isPermissionUpload());
        assertTrue(perms.isPermissionDownload());
        assertTrue(perms.isPermissionAccessTaskManager());
        assertTrue(perms.isPermissionManageGlobalPreferences());

        assertTrue(perms.isPermissionEditMetadata());
        assertTrue(perms.isPermissionManageMetadataConfig());
        assertTrue(perms.isPermissionAccessBookdrop());

        assertFalse(perms.isPermissionDeleteBook());
        assertFalse(perms.isPermissionManageIcons());
    }

    @Test
    void syncUserPermissionsFromGroups_RevokesManagedPermissions() {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername("revokedUser");
        UserPermissionsEntity perms = new UserPermissionsEntity();
        perms.setUser(user);
        perms.setPermissionUpload(true); // User currently has this
        perms.setPermissionManageIcons(true); // User has this (unmanaged in this test)
        user.setPermissions(perms);

        List<String> groups = List.of("viewers"); // Does NOT have upload
        OidcAutoProvisionDetails provisionDetails = new OidcAutoProvisionDetails();
        provisionDetails.setSyncPermissions(true);

        OidcProviderDetails providerDetails = new OidcProviderDetails();
        Map<String, List<String>> roleMappings = new HashMap<>();
        roleMappings.put("uploaders", List.of("permissionUpload")); // Managed permission

        providerDetails.setRoleMappings(roleMappings);

        AppSettings appSettings = AppSettings.builder().oidcProviderDetails(providerDetails).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        userPersistenceService.syncUserPermissionsFromGroups(user, groups, provisionDetails);

        verify(userRepository).save(user);

        assertFalse(perms.isPermissionUpload(), "Should revoke managed permission if not in groups");
        assertTrue(perms.isPermissionManageIcons(), "Should NOT revoke unmanaged permission");
    }
}