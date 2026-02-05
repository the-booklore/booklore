package org.booklore.service.event;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookFile;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.service.NotificationService;
import org.booklore.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                .primaryFile(BookFile.builder().fileName("test-book.pdf").bookType(BookFileType.PDF).build())
                .libraryId(1L)
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
                .primaryFile(BookFile.builder().fileName("test-book.pdf").bookType(BookFileType.PDF).build())
                .libraryId(1L)
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