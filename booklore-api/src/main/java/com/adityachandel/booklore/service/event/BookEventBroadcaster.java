package com.adityachandel.booklore.service.event;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.websocket.LogNotification;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.user.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class BookEventBroadcaster {

    private final NotificationService notificationService;
    private final UserService userService;

    @Async
    public void broadcastBookAddEvent(Book book) {
        Long libraryId = book.getLibraryId();
        userService.getBookLoreUsers().stream()
                .filter(u -> u.getPermissions().isAdmin() || u.getAssignedLibraries().stream()
                        .anyMatch(lib -> lib.getId().equals(libraryId)))
                .forEach(u -> {
                    notificationService.sendMessageToUser(u.getUsername(), Topic.BOOK_ADD, book);
                    notificationService.sendMessageToUser(u.getUsername(), Topic.LOG, LogNotification.info("Book added: " + book.getFileName()));
                });
    }
}
