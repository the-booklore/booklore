package org.booklore.service.event;

import lombok.AllArgsConstructor;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.service.NotificationService;
import org.booklore.service.user.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@AllArgsConstructor
@Service
public class AdminEventBroadcaster {

    private final NotificationService notificationService;
    private final UserService userService;

    @Async
    public void broadcastAdminEvent(String message) {
        List<BookLoreUser> admins = userService.getBookLoreUsers().stream()
                .filter(u -> u.getPermissions().isAdmin())
                .toList();
        for (BookLoreUser admin : admins) {
            notificationService.sendMessageToUser(admin.getUsername(), Topic.LOG, LogNotification.info(message));
        }
    }
}
