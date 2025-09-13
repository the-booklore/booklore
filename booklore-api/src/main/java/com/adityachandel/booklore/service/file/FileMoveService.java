package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.util.PathPatternResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class FileMoveService {

    private static final int BATCH_SIZE = 100;

    private final BookQueryService bookQueryService;
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final NotificationService notificationService;
    private final UnifiedFileMoveService unifiedFileMoveService;

    public void moveFiles(FileMoveRequest request) {
        Set<Long> bookIds = request.getBookIds();
        log.info("Moving {} books in batches of {}", bookIds.size(), BATCH_SIZE);

        List<Book> allUpdatedBooks = new ArrayList<>();
        int totalProcessed = 0;
        int offset = 0;

        while (offset < bookIds.size()) {
            log.info("Processing batch {}/{}", (offset / BATCH_SIZE) + 1, (bookIds.size() + BATCH_SIZE - 1) / BATCH_SIZE);

            List<BookEntity> batchBooks = bookQueryService.findWithMetadataByIdsWithPagination(bookIds, offset, BATCH_SIZE);

            if (batchBooks.isEmpty()) {
                log.info("No more books at offset {}", offset);
                break;
            }

            List<Book> batchUpdatedBooks = processBookChunk(batchBooks);
            allUpdatedBooks.addAll(batchUpdatedBooks);

            totalProcessed += batchBooks.size();
            offset += BATCH_SIZE;

            log.info("Processed {}/{} books", totalProcessed, bookIds.size());
        }

        log.info("Move completed: {} books processed, {} updated", totalProcessed, allUpdatedBooks.size());
        sendUpdateNotifications(allUpdatedBooks);
    }

    public String generatePathFromPattern(BookEntity book, String pattern) {
        return PathPatternResolver.resolvePattern(book, pattern);
    }

    private List<Book> processBookChunk(List<BookEntity> books) {
        List<Book> updatedBooks = new ArrayList<>();

        unifiedFileMoveService.moveBatchBookFiles(books, new UnifiedFileMoveService.BatchMoveCallback() {
            @Override
            public void onBookMoved(BookEntity book) {
                bookRepository.save(book);
                updatedBooks.add(bookMapper.toBook(book));
            }

            @Override
            public void onBookMoveFailed(BookEntity book, Exception error) {
                log.error("Move failed for book {}: {}", book.getId(), error.getMessage(), error);
                throw new RuntimeException("File move failed for book id " + book.getId(), error);
            }
        });

        return updatedBooks;
    }

    private void sendUpdateNotifications(List<Book> updatedBooks) {
        if (!updatedBooks.isEmpty()) {
            notificationService.sendMessage(Topic.BOOK_METADATA_BATCH_UPDATE, updatedBooks);
        }
    }
}
