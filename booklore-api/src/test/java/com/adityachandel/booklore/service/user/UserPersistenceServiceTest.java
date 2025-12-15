package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.UserPermissionsEntity;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void provisionOidcUserInternal_SetsPermissionChangePassword_False() {
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
        assertFalse(savedUser.getPermissions().isPermissionChangePassword(), "OIDC users should not have permission to change password");
    }
}
