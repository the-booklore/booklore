package com.adityachandel.booklore.service.event;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.websocket.LogNotification;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.user.UserService;
import lombok.AllArgsConstructor;
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
