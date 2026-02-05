package com.adityachandel.booklore.service.event;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminEventBroadcasterTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    private AdminEventBroadcaster adminEventBroadcaster;

    @BeforeEach
    void setUp() {
        adminEventBroadcaster = new AdminEventBroadcaster(notificationService, userService);
    }

    @Test
    void broadcastAdminEvent_withAdminUsers_sendsNotifications() {
        String message = "Test admin message";

        BookLoreUser adminUser = new BookLoreUser();
        adminUser.setUsername("admin");
        BookLoreUser.UserPermissions adminPermissions = new BookLoreUser.UserPermissions();
        adminPermissions.setAdmin(true);
        adminUser.setPermissions(adminPermissions);

        BookLoreUser anotherAdminUser = new BookLoreUser();
        anotherAdminUser.setUsername("admin2");
        BookLoreUser.UserPermissions anotherAdminPermissions = new BookLoreUser.UserPermissions();
        anotherAdminPermissions.setAdmin(true);
        anotherAdminUser.setPermissions(anotherAdminPermissions);

        when(userService.getBookLoreUsers()).thenReturn(List.of(adminUser, anotherAdminUser));

        adminEventBroadcaster.broadcastAdminEvent(message);

        await().untilAsserted(() -> {
            verify(notificationService).sendMessageToUser(eq("admin"), eq(Topic.LOG), any());
            verify(notificationService).sendMessageToUser(eq("admin2"), eq(Topic.LOG), any());
        });
    }

    @Test
    void broadcastAdminEvent_withNoAdminUsers_sendsNoNotifications() {
        String message = "Test admin message";

        BookLoreUser regularUser = new BookLoreUser();
        regularUser.setUsername("regular");
        BookLoreUser.UserPermissions regularPermissions = new BookLoreUser.UserPermissions();
        regularPermissions.setAdmin(false);
        regularUser.setPermissions(regularPermissions);

        when(userService.getBookLoreUsers()).thenReturn(List.of(regularUser));

        adminEventBroadcaster.broadcastAdminEvent(message);

        await().untilAsserted(() -> {
            verify(notificationService, never()).sendMessageToUser(any(), eq(Topic.LOG), any());
        });
    }
}