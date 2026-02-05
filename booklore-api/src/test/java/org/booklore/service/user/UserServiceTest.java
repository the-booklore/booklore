package org.booklore.service.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.ChangePasswordRequest;
import org.booklore.model.dto.request.UserUpdateRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.model.enums.ProvisioningMethod;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private BookLoreUserTransformer bookLoreUserTransformer;

    @InjectMocks
    private UserService userService;

    @Test
    void changePassword_OidcUser_SkipsCurrentPasswordCheck() {
        Long userId = 1L;
        BookLoreUser authenticatedUser = BookLoreUser.builder().id(userId).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        userEntity.setId(userId);
        userEntity.setProvisioningMethod(ProvisioningMethod.OIDC);
        userEntity.setPasswordHash(UserPersistenceService.OIDC_LOCKED_PASSWORD_HASH);
        userEntity.setPermissions(UserPermissionsEntity.builder().permissionChangePassword(true).build());

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        
        // Check for same password is now also skipped for locked OIDC users, so we don't expect matches to be called at all
        when(passwordEncoder.encode("newPass")).thenReturn("newHash");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrongCurrent"); 
        request.setNewPassword("newPass");

        userService.changePassword(request);

        verify(passwordEncoder, never()).matches(eq("wrongCurrent"), any());
        verify(userRepository).saveAndFlush(userEntity);
    }

    @Test
    void changePassword_OidcUser_WithPasswordSet_EnforcesCurrentPasswordCheck() {
        Long userId = 1L;
        BookLoreUser authenticatedUser = BookLoreUser.builder().id(userId).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        userEntity.setId(userId);
        userEntity.setProvisioningMethod(ProvisioningMethod.OIDC);
        userEntity.setPasswordHash("existingHash"); // Not the locked hash
        userEntity.setPermissions(UserPermissionsEntity.builder().permissionChangePassword(true).build());

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(passwordEncoder.matches("wrongCurrent", "existingHash")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrongCurrent");
        request.setNewPassword("newPass");

        assertThrows(APIException.class, () -> userService.changePassword(request));
    }

    @Test
    void changePassword_LocalUser_EnforcesCurrentPasswordCheck() {
        Long userId = 1L;
        BookLoreUser authenticatedUser = BookLoreUser.builder().id(userId).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        userEntity.setId(userId);
        userEntity.setProvisioningMethod(ProvisioningMethod.LOCAL);
        userEntity.setPasswordHash("correctHash");
        userEntity.setPermissions(UserPermissionsEntity.builder().permissionChangePassword(true).build());

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(passwordEncoder.matches("wrongPass", "correctHash")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrongPass");
        request.setNewPassword("newPass");

        assertThrows(APIException.class, () -> userService.changePassword(request));
    }

    @Test
    void changePassword_UserWithoutPermission_ThrowsException() {
        Long userId = 1L;
        BookLoreUser authenticatedUser = BookLoreUser.builder().id(userId).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        userEntity.setId(userId);
        userEntity.setProvisioningMethod(ProvisioningMethod.LOCAL);
        userEntity.setPermissions(UserPermissionsEntity.builder().permissionChangePassword(false).build());

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));

        ChangePasswordRequest request = new ChangePasswordRequest();

        APIException exception = assertThrows(APIException.class, () -> userService.changePassword(request));
        // Verify generic message
        assert(exception.getMessage().contains("You do not have permission"));
    }

    @Test
    void changePassword_OidcUserWithoutPermission_ThrowsSpecificException() {
        Long userId = 1L;
        BookLoreUser authenticatedUser = BookLoreUser.builder().id(userId).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        userEntity.setId(userId);
        userEntity.setProvisioningMethod(ProvisioningMethod.OIDC);
        userEntity.setPermissions(UserPermissionsEntity.builder().permissionChangePassword(false).build());

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));

        ChangePasswordRequest request = new ChangePasswordRequest();

        APIException exception = assertThrows(APIException.class, () -> userService.changePassword(request));
        // Verify specific OIDC message
        assert(exception.getMessage().contains("managed by your SSO provider"));
    }

    @Test
    void updateUser_UpdatesChangePasswordPermission() {
        Long userId = 1L;
        Long targetUserId = 2L;
        
        // Admin user doing the update
        BookLoreUser.UserPermissions adminPermissions = new BookLoreUser.UserPermissions();
        adminPermissions.setAdmin(true);
        
        BookLoreUser adminUser = BookLoreUser.builder()
                .id(userId)
                .permissions(adminPermissions)
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(adminUser);

        BookLoreUserEntity targetUser = new BookLoreUserEntity();
        targetUser.setId(targetUserId);
        targetUser.setPermissions(new UserPermissionsEntity());
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));

        UserUpdateRequest request = new UserUpdateRequest();
        UserUpdateRequest.Permissions permissions = new UserUpdateRequest.Permissions();
        permissions.setCanChangePassword(false);
        request.setPermissions(permissions);

        userService.updateUser(targetUserId, request);

        assertFalse(targetUser.getPermissions().isPermissionChangePassword());
    }
}