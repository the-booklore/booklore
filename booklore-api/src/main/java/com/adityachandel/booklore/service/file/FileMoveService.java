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
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.adityachandel.booklore.model.enums.PermissionType.ADMIN;
import static com.adityachandel.booklore.model.enums.PermissionType.MANIPULATE_LIBRARY;

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

    @Transactional
    public void moveFiles(FileMoveRequest request) {
        List<Long> bookIds = request.getBookIds().stream().toList();
        List<FileMoveRequest.Move> moves = request.getMoves() != null ? request.getMoves() : List.of();

        log.info("Moving {} books with {} specific moves in batches of {}", bookIds.size(), moves.size(), BATCH_SIZE);

        Map<Long, Long> bookToTargetLibraryMap = moves.stream()
                .collect(Collectors.toMap(
                        FileMoveRequest.Move::getBookId,
                        FileMoveRequest.Move::getTargetLibraryId
                ));

        Map<Long, List<Book>> libraryRemovals = new HashMap<>();
        Map<Long, List<Book>> libraryAdditions = new HashMap<>();
        List<Book> allUpdatedBooks = new ArrayList<>();
        int totalProcessed = 0;
        int offset = 0;

        while (offset < bookIds.size()) {
            log.info("Processing batch {}/{}", (offset / BATCH_SIZE) + 1, (bookIds.size() + BATCH_SIZE - 1) / BATCH_SIZE);

            List<Long> batchBookIds = bookIds.subList(offset, Math.min(offset + BATCH_SIZE, bookIds.size()));
            Set<Long> batchBookIdSet = new HashSet<>(batchBookIds);

            List<BookEntity> batchBooks = bookQueryService.findWithMetadataByIdsWithPagination(batchBookIdSet, offset, BATCH_SIZE);

            if (batchBooks.isEmpty()) {
                log.info("No more books at offset {}", offset);
                break;
            }

            List<Book> batchUpdatedBooks = processBookChunk(batchBooks, bookToTargetLibraryMap, libraryRemovals, libraryAdditions);
            allUpdatedBooks.addAll(batchUpdatedBooks);

            totalProcessed += batchBooks.size();
            offset += BATCH_SIZE;

            log.info("Processed {}/{} books", totalProcessed, bookIds.size());
        }

        log.info("Move completed: {} books processed, {} updated", totalProcessed, allUpdatedBooks.size());
        sendUpdateNotifications(allUpdatedBooks);
        sendCrossLibraryMoveNotifications(libraryRemovals, libraryAdditions);

    }

    public String generatePathFromPattern(BookEntity book, String pattern) {
        return PathPatternResolver.resolvePattern(book, pattern);
    }

    private List<Book> processBookChunk(List<BookEntity> books, Map<Long, Long> bookToTargetLibraryMap, Map<Long, List<Book>> libraryRemovals, Map<Long, List<Book>> libraryAdditions) {
        List<Book> updatedBooks = new ArrayList<>();

        Map<Long, Long> originalLibraryIds = new HashMap<>();
        for (BookEntity book : books) {
            if (book.getLibraryPath() != null && book.getLibraryPath().getLibrary() != null) {
                originalLibraryIds.put(book.getId(), book.getLibraryPath().getLibrary().getId());
            }
        }

        unifiedFileMoveService.moveBatchBookFiles(books, bookToTargetLibraryMap, new UnifiedFileMoveService.BatchMoveCallback() {
            @Override
            public void onBookMoved(BookEntity book) {
                Long targetLibraryId = bookToTargetLibraryMap.get(book.getId());
                Long originalSourceLibraryId = originalLibraryIds.get(book.getId());

                log.debug("Processing moved book {}: targetLibraryId={}, originalSourceLibraryId={}", book.getId(), targetLibraryId, originalSourceLibraryId);

                if (targetLibraryId != null && originalSourceLibraryId != null && !targetLibraryId.equals(originalSourceLibraryId)) {
                    log.info("Cross-library move detected for book {}: {} -> {}", book.getId(), originalSourceLibraryId, targetLibraryId);

                    Book bookDtoForRemoval = bookMapper.toBookWithDescription(book, false);
                    bookDtoForRemoval.setLibraryId(originalSourceLibraryId);
                    libraryRemovals.computeIfAbsent(originalSourceLibraryId, k -> new ArrayList<>()).add(bookDtoForRemoval);
                    log.debug("Added book {} to removal list for library {}", book.getId(), originalSourceLibraryId);

                    bookRepository.updateLibraryId(book.getId(), targetLibraryId);
                    log.debug("Updated library_id for book {} to {}", book.getId(), targetLibraryId);

                    BookEntity savedBook = bookRepository.saveAndFlush(book);

                    Book updatedBookDto = bookMapper.toBookWithDescription(savedBook, false);
                    updatedBookDto.setLibraryId(targetLibraryId);
                    libraryAdditions.computeIfAbsent(targetLibraryId, k -> new ArrayList<>()).add(updatedBookDto);
                    log.debug("Added book {} to addition list for library {} with libraryId {}", book.getId(), targetLibraryId, updatedBookDto.getLibraryId());

                    updatedBooks.add(updatedBookDto);
                } else {
                    log.debug("Same library move for book {} or no target specified. Target: {}, Original: {}", book.getId(), targetLibraryId, originalSourceLibraryId);
                    BookEntity savedBook = bookRepository.save(book);
                    updatedBooks.add(bookMapper.toBook(savedBook));
                }
            }

            @Override
            public void onBookMoveFailed(BookEntity book, Exception error) {
                log.error("Move failed for book {}: {}", book.getId(), error.getMessage(), error);
                throw new RuntimeException("File move failed for book id " + book.getId(), error);
            }
        });

        log.info("Processed {} books, {} library removals tracked, {} library additions tracked", updatedBooks.size(), libraryRemovals.size(), libraryAdditions.size());

        return updatedBooks;
    }

    private void sendUpdateNotifications(List<Book> updatedBooks) {
        if (!updatedBooks.isEmpty()) {
            notificationService.sendMessage(Topic.BOOK_METADATA_BATCH_UPDATE, updatedBooks);
        }
    }

    private void sendCrossLibraryMoveNotifications(Map<Long, List<Book>> libraryRemovals, Map<Long, List<Book>> libraryAdditions) {
        log.info("Sending cross-library move notifications: {} removals, {} additions",
                libraryRemovals.size(), libraryAdditions.size());

        for (Map.Entry<Long, List<Book>> entry : libraryRemovals.entrySet()) {
            List<Long> removedBookIds = entry.getValue().stream()
                    .map(Book::getId)
                    .collect(Collectors.toList());

            log.info("Notifying removal of {} books from library {}: {}", removedBookIds.size(), entry.getKey(), removedBookIds);
            try {
                notificationService.sendMessageToPermissions(Topic.BOOKS_REMOVE, removedBookIds, Set.of(ADMIN, MANIPULATE_LIBRARY));
                log.info("Successfully sent removal notification for library {}", entry.getKey());
            } catch (Exception e) {
                log.error("Failed to send removal notification for library {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }

        for (Map.Entry<Long, List<Book>> entry : libraryAdditions.entrySet()) {
            List<Book> addedBooks = entry.getValue();

            log.info("Notifying addition of {} books to library {}", addedBooks.size(), entry.getKey());
            try {
                for (Book book : addedBooks) {
                    log.debug("Sending BOOK_ADD notification for book {} to library {}", book.getId(), entry.getKey());
                    notificationService.sendMessageToPermissions(Topic.BOOK_ADD, book, Set.of(ADMIN, MANIPULATE_LIBRARY));
                }
                log.info("Successfully sent {} addition notifications for library {}", addedBooks.size(), entry.getKey());
            } catch (Exception e) {
                log.error("Failed to send addition notifications for library {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }
    }
}
