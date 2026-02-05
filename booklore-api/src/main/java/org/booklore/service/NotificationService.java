package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.model.enums.PermissionType;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.booklore.util.UserPermissionUtils.hasPermission;

@Slf4j
@Service
@AllArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    public void sendMessage(Topic topic, Object message) {
        try {
            var user = authenticationService.getAuthenticatedUser();
            if (user == null) {
                log.warn("No authenticated user found. Message not sent: {}", topic);
                return;
            }
            String username = user.getUsername();
            messagingTemplate.convertAndSendToUser(username, topic.getPath(), message);
        } catch (Exception e) {
            log.error("Error sending message to topic {}: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * Send message to a specific user by username
     */
    public void sendMessageToUser(String username, Topic topic, Object message) {
        try {
            messagingTemplate.convertAndSendToUser(username, topic.getPath(), message);
        } catch (Exception e) {
            log.error("Error sending message to user {} for topic {}: {}", username, topic, e.getMessage(), e);
        }
    }

    public CompletableFuture<Void> sendAsyncMessage(Topic topic, Object message) {
        var user = authenticationService.getAuthenticatedUser();
        if (user == null) {
            log.warn("No authenticated user found. Message not sent: {}", topic);
            return CompletableFuture.completedFuture(null);
        }
        String username = user.getUsername();
        return CompletableFuture.runAsync(() ->
                messagingTemplate.convertAndSendToUser(username, topic.getPath(), message)
        );
    }

    @Async
    public CompletableFuture<Void> sendAsyncMessageToUser(String username, Topic topic, Object message) {
        messagingTemplate.convertAndSendToUser(username, topic.getPath(), message);
        return CompletableFuture.completedFuture(null);
    }

    public void sendMessageToPermissions(Topic topic, Object message, Set<PermissionType> permissionTypes) {
        if (permissionTypes == null || permissionTypes.isEmpty()) return;

        List<BookLoreUserEntity> users = userRepository.findByPermissions_PermissionTypeIn(permissionTypes);
        for (BookLoreUserEntity user : users) {
            messagingTemplate.convertAndSendToUser(user.getUsername(), topic.getPath(), message);
        }
    }

    @Async
    public CompletableFuture<Void> sendAsyncMessageToPermissions(Topic topic, Object message, Set<PermissionType> permissionTypes) {
        if (permissionTypes == null || permissionTypes.isEmpty()) return CompletableFuture.completedFuture(null);

        List<BookLoreUserEntity> users = userRepository.findByPermissions_PermissionTypeIn(permissionTypes);
        for (BookLoreUserEntity user : users) {
            messagingTemplate.convertAndSendToUser(user.getUsername(), topic.getPath(), message);
        }
        return CompletableFuture.completedFuture(null);
    }
}