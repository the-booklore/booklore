package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.enums.PermissionType;
import org.booklore.model.websocket.Topic;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AuthenticationService authenticationService;
    private final EntityManager entityManager;

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

    @Transactional(readOnly = true)
    public void sendMessageToPermissions(Topic topic, Object message, Set<PermissionType> permissionTypes) {
        if (permissionTypes == null || permissionTypes.isEmpty()) return;

        try {
            List<String> usernames = findUsernamesWithPermissions(permissionTypes);
            for (String username : usernames) {
                messagingTemplate.convertAndSendToUser(username, topic.getPath(), message);
            }
        } catch (Exception e) {
            log.error("Error sending message to users with permissions {}: {}", permissionTypes, e.getMessage(), e);
        }
    }

    private List<String> findUsernamesWithPermissions(Set<PermissionType> permissionTypes) {
        String conditions = permissionTypes.stream()
                .map(p -> "p." + p.getEntityField() + " = true")
                .collect(Collectors.joining(" OR "));
        String jpql = "SELECT u.username FROM BookLoreUserEntity u JOIN u.permissions p WHERE " + conditions;
        return entityManager.createQuery(jpql, String.class).getResultList();
    }
}
