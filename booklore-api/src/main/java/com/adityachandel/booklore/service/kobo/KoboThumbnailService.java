package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.service.book.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboThumbnailService {

    private final BookService bookService;

    public ResponseEntity<Resource> getThumbnail(Long bookId) {
        return getThumbnailInternal(bookId);
    }

    private ResponseEntity<Resource> getThumbnailInternal(Long bookId) {

        Resource image = bookService.getBookCover(bookId);
        if (!isValidImage(image)) {
            log.warn("Thumbnail not found for bookId={}", bookId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                .body(image);
    }

    private boolean isValidImage(Resource image) {
        return image != null && image.exists();
    }
}