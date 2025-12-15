package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.config.security.service.DynamicOidcJwtProcessor;
import com.adityachandel.booklore.model.dto.UserCreateRequest;
import com.adityachandel.booklore.model.dto.request.InitialUserRequest;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
