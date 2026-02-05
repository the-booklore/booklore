package com.adityachandel.booklore.service.event;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.websocket.LogNotification;
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
class BookEventBroadcasterTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    private BookEventBroadcaster bookEventBroadcaster;

    @BeforeEach
    void setUp() {
        bookEventBroadcaster = new BookEventBroadcaster(notificationService, userService);
    }

    @Test
    void broadcastBookAddEvent_withAdminUser_sendsNotifications() {
        Book book = Book.builder()
                .id(1L)
                .fileName("test-book.pdf")
                .libraryId(1L)
                .bookType(BookFileType.PDF)
                .build();

        BookLoreUser adminUser = new BookLoreUser();
        adminUser.setUsername("admin");
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        adminUser.setPermissions(permissions);

        when(userService.getBookLoreUsers()).thenReturn(List.of(adminUser));

        bookEventBroadcaster.broadcastBookAddEvent(book);

        await().untilAsserted(() -> {
            verify(notificationService).sendMessageToUser(eq("admin"), eq(Topic.BOOK_ADD), eq(book));
            verify(notificationService).sendMessageToUser(eq("admin"), eq(Topic.LOG), any(LogNotification.class));
        });
    }

    @Test
    void broadcastBookAddEvent_withLibraryUser_sendsNotifications() {
        Book book = Book.builder()
                .id(1L)
                .fileName("test-book.pdf")
                .libraryId(1L)
                .bookType(BookFileType.PDF)
                .build();

        BookLoreUser libraryUser = new BookLoreUser();
        libraryUser.setUsername("libraryuser");
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(false);
        libraryUser.setPermissions(permissions);

        // Create a library that matches the book's library ID
        Library library = Library.builder()
                .id(1L)  // Same as book's libraryId
                .name("Test Library")
                .build();
        libraryUser.setAssignedLibraries(List.of(library));

        when(userService.getBookLoreUsers()).thenReturn(List.of(libraryUser));

        bookEventBroadcaster.broadcastBookAddEvent(book);

        await().untilAsserted(() -> {
            verify(notificationService).sendMessageToUser(eq("libraryuser"), eq(Topic.BOOK_ADD), eq(book));
            verify(notificationService).sendMessageToUser(eq("libraryuser"), eq(Topic.LOG), any(LogNotification.class));
        });
    }
}