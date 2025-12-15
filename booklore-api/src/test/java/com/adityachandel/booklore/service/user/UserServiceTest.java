package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.ChangePasswordRequest;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

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

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        
        // Check for same password is now also skipped for locked OIDC users, so we don't expect matches to be called at all
        when(passwordEncoder.encode("newPass")).thenReturn("newHash");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrongCurrent"); 
        request.setNewPassword("newPass");

        userService.changePassword(request);

        verify(passwordEncoder, never()).matches(eq("wrongCurrent"), any());
        verify(userRepository).save(userEntity);
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

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(passwordEncoder.matches("wrongPass", "correctHash")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrongPass");
        request.setNewPassword("newPass");

        assertThrows(APIException.class, () -> userService.changePassword(request));
    }
}
