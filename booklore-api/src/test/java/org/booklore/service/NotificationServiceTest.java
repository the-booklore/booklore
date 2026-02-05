package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.enums.PermissionType;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private UserRepository userRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(messagingTemplate, authenticationService, userRepository);
    }

    @Test
    void sendMessage_withAuthenticatedUser_sendsMessage() {
        BookLoreUser user = new BookLoreUser();
        user.setUsername("testuser");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        Topic topic = Topic.BOOK_ADD;
        String message = "Test message";

        notificationService.sendMessage(topic, message);

        verify(messagingTemplate).convertAndSendToUser(eq("testuser"), eq(topic.getPath()), eq(message));
    }

    @Test
    void sendMessage_withoutAuthenticatedUser_logsWarning() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(null);

        Topic topic = Topic.BOOK_ADD;
        String message = "Test message";

        notificationService.sendMessage(topic, message);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void sendAsyncMessage_withAuthenticatedUser_sendsMessage() throws Exception {
        BookLoreUser user = new BookLoreUser();
        user.setUsername("testuser");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        Topic topic = Topic.BOOK_ADD;
        String message = "Test message";

        CompletableFuture<Void> future = notificationService.sendAsyncMessage(topic, message);
        future.get(); // Wait for async execution

        verify(messagingTemplate).convertAndSendToUser(eq("testuser"), eq(topic.getPath()), eq(message));
    }

    @Test
    void sendAsyncMessage_withoutAuthenticatedUser_logsWarning() throws Exception {
        when(authenticationService.getAuthenticatedUser()).thenReturn(null);

        Topic topic = Topic.BOOK_ADD;
        String message = "Test message";

        CompletableFuture<Void> future = notificationService.sendAsyncMessage(topic, message);
        future.get(); // Wait for async execution

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void sendMessageToPermissions_withUsers_sendsToCorrectUsers() {
        Set<PermissionType> permissions = new HashSet<>();
        permissions.add(PermissionType.ADMIN);

        BookLoreUserEntity adminUser = new BookLoreUserEntity();
        adminUser.setUsername("admin");
        
        BookLoreUserEntity regularUser = new BookLoreUserEntity();
        regularUser.setUsername("regular");

        List<BookLoreUserEntity> users = List.of(adminUser, regularUser);
        when(userRepository.findByPermissions_PermissionTypeIn(permissions)).thenReturn(users);

        Topic topic = Topic.LOG;
        String message = "Permission message";

        notificationService.sendMessageToPermissions(topic, message, permissions);

        verify(messagingTemplate).convertAndSendToUser(eq("admin"), eq(topic.getPath()), eq(message));
        verify(messagingTemplate).convertAndSendToUser(eq("regular"), eq(topic.getPath()), eq(message));
    }

    @Test
    void sendMessageToPermissions_withEmptyPermissions_doesNotSend() {
        Set<PermissionType> permissions = Collections.emptySet();
        Topic topic = Topic.LOG;
        String message = "Permission message";

        notificationService.sendMessageToPermissions(topic, message, permissions);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void sendAsyncMessageToPermissions_withUsers_sendsToCorrectUsers() throws Exception {
        Set<PermissionType> permissions = new HashSet<>();
        permissions.add(PermissionType.ADMIN);

        BookLoreUserEntity adminUser = new BookLoreUserEntity();
        adminUser.setUsername("admin");
        
        BookLoreUserEntity regularUser = new BookLoreUserEntity();
        regularUser.setUsername("regular");

        List<BookLoreUserEntity> users = List.of(adminUser, regularUser);
        when(userRepository.findByPermissions_PermissionTypeIn(permissions)).thenReturn(users);

        Topic topic = Topic.LOG;
        String message = "Permission message";

        CompletableFuture<Void> future = notificationService.sendAsyncMessageToPermissions(topic, message, permissions);
        future.get(); // Wait for async execution

        verify(messagingTemplate).convertAndSendToUser(eq("admin"), eq(topic.getPath()), eq(message));
        verify(messagingTemplate).convertAndSendToUser(eq("regular"), eq(topic.getPath()), eq(message));
    }

    @Test
    void sendAsyncMessageToPermissions_withEmptyPermissions_doesNotSend() throws Exception {
        Set<PermissionType> permissions = Collections.emptySet();
        Topic topic = Topic.LOG;
        String message = "Permission message";

        CompletableFuture<Void> future = notificationService.sendAsyncMessageToPermissions(topic, message, permissions);
        future.get(); // Wait for async execution

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }
}