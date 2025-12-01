package com.adityachandel.booklore.model.websocket;

import com.adityachandel.booklore.model.dto.Book;
import lombok.Data;

@Data
public class BookAddNotification {
    private Book addedBook;
}
