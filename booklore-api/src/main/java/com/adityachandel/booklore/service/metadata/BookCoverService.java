package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.websocket.LogNotification;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.book.BookQueryService;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriter;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriterFactory;
import com.adityachandel.booklore.util.BookCoverUtils;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.SecurityContextVirtualThread;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BookCoverService {

    private static final int BATCH_SIZE = 100;

    private final BookRepository bookRepository;
    private final BookMetadataMapper bookMetadataMapper;
    private final NotificationService notificationService;
    private final AppSettingService appSettingService;
    private final FileService fileService;
    private final BookFileProcessorRegistry processorRegistry;
    private final BookQueryService bookQueryService;
    private final CoverImageGenerator coverImageGenerator;
    private final MetadataWriterFactory metadataWriterFactory;
    private final TransactionTemplate transactionTemplate;

    private record BookCoverInfo(Long id, String title) {
    }

    private record BookRegenerationInfo(Long id, String title, BookFileType bookType) {
    }

    public void generateCustomCover(long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (isCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }
        String title = bookEntity.getMetadata().getTitle();
        String author = getAuthorNames(bookEntity);
        byte[] coverBytes = coverImageGenerator.generateCover(title, author);
        fileService.createThumbnailFromBytes(bookId, coverBytes);
        updateBookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
    }

    @Transactional
    public BookMetadata updateCoverImageFromFile(Long bookId, MultipartFile file) {
        fileService.createThumbnailFromFile(bookId, file);
        return updateCover(bookId, (writer, book) -> writer.replaceCoverImageFromUpload(book, file));
    }

    @Transactional
    public BookMetadata updateCoverImageFromUrl(Long bookId, String url) {
        fileService.createThumbnailFromUrl(bookId, url);
        return updateCover(bookId, (writer, book) -> writer.replaceCoverImageFromUrl(book, url));
    }

    public void updateCoverImageFromFileForBooks(Set<Long> bookIds, MultipartFile file) {
        validateCoverFile(file);
        byte[] coverImageBytes = extractBytesFromMultipartFile(file);
        List<BookCoverInfo> unlockedBooks = getUnlockedBookCoverInfos(bookIds);
        SecurityContextVirtualThread.runWithSecurityContext(() -> processBulkCoverUpdate(unlockedBooks, coverImageBytes));
    }

    public void regenerateCover(long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (isCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        regenerateCoverForBook(bookEntity, "");
        updateBookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
    }

    public void regenerateCoversForBooks(Set<Long> bookIds) {
        List<BookRegenerationInfo> unlockedBooks = getUnlockedBookRegenerationInfos(bookIds);
        SecurityContextVirtualThread.runWithSecurityContext(() -> processBulkCoverRegeneration(unlockedBooks));
    }

    public void regenerateCovers() {
        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
                List<BookEntity> books = bookQueryService.getAllFullBookEntities().stream()
                        .filter(book -> !isCoverLocked(book))
                        .toList();
                int total = books.size();
                notificationService.sendMessage(Topic.LOG, LogNotification.info("Started regenerating covers for " + total + " books"));

                int current = 1;
                for (BookEntity book : books) {
                    try {
                        String progress = "(" + current + "/" + total + ") ";
                        regenerateCoverForBook(book, progress);
                    } catch (Exception e) {
                        log.error("Failed to regenerate cover for book ID {}: {}", book.getId(), e.getMessage(), e);
                    }
                    current++;
                }
                notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished regenerating covers"));
            } catch (Exception e) {
                log.error("Error during cover regeneration: {}", e.getMessage(), e);
                notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during cover regeneration"));
            }
        });
    }

    private BookMetadata updateCover(Long bookId, BiConsumer<MetadataWriter, BookEntity> writerAction) {
        BookEntity bookEntity = bookRepository.findAllWithMetadataByIds(Collections.singleton(bookId)).stream()
                .findFirst()
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        updateBookCoverMetadata(bookEntity);

        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        boolean saveToOriginalFile = settings.isSaveToOriginalFile();
        boolean convertCbrCb7ToCbz = settings.isConvertCbrCb7ToCbz();

        if (saveToOriginalFile && (bookEntity.getBookType() != BookFileType.CBX || convertCbrCb7ToCbz)) {
            metadataWriterFactory.getWriter(bookEntity.getBookType())
                    .ifPresent(writer -> {
                        writerAction.accept(writer, bookEntity);
                        String newHash = FileFingerprint.generateHash(bookEntity.getFullFilePath());
                        bookEntity.setCurrentHash(newHash);
                    });
        }

        bookEntity.setMetadataUpdatedAt(Instant.now());
        bookRepository.save(bookEntity);
        return bookMetadataMapper.toBookMetadata(bookEntity.getMetadata(), true);
    }

    private void processBulkCoverUpdate(List<BookCoverInfo> books, byte[] coverImageBytes) {
        try {
            int total = books.size();
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Started updating covers for " + total + " selected book(s)"));

            int current = 1;
            for (BookCoverInfo bookInfo : books) {
                try {
                    String progress = "(" + current + "/" + total + ") ";
                    notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Updating cover for: " + bookInfo.title()));
                    fileService.createThumbnailFromBytes(bookInfo.id(), coverImageBytes);

                    bookRepository.findById(bookInfo.id()).ifPresent(book -> {
                        updateBookCoverMetadata(book);
                        bookRepository.save(book);
                    });

                    log.info("{}Successfully updated cover for book ID {} ({})", progress, bookInfo.id(), bookInfo.title());
                } catch (Exception e) {
                    log.error("Failed to update cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                }
                pauseAfterBatchIfNeeded(current, total);
                current++;
            }
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished updating covers for selected books"));
        } catch (Exception e) {
            log.error("Error during cover update: {}", e.getMessage(), e);
            notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during cover update"));
        }
    }

    private void processBulkCoverRegeneration(List<BookRegenerationInfo> books) {
        try {
            int total = books.size();
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Started regenerating covers for " + total + " selected book(s)"));

            int current = 1;
            List<Long> refreshedIds = new ArrayList<>();

            for (BookRegenerationInfo bookInfo : books) {
                try {
                    String progress = "(" + current + "/" + total + ") ";
                    notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Regenerating cover for: " + bookInfo.title()));
                    regenerateCoverForBookId(bookInfo);
                    log.info("{}Successfully regenerated cover for book ID {} ({})", progress, bookInfo.id(), bookInfo.title());
                    refreshedIds.add(bookInfo.id());
                } catch (Exception e) {
                    log.error("Failed to regenerate cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                }
                pauseAfterBatchIfNeeded(current, total);
                current++;
            }

            notifyBulkCoverUpdate(refreshedIds);
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished regenerating covers for selected books"));
        } catch (Exception e) {
            log.error("Error during cover regeneration: {}", e.getMessage(), e);
            notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during cover regeneration"));
        }
    }

    private void regenerateCoverForBook(BookEntity book, String progress) {
        String title = book.getMetadata().getTitle();
        notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Regenerating cover for: " + title));

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(book.getBookType());
        boolean success = processor.generateCover(book);

        if (success) {
            Instant now = Instant.now();
            book.getMetadata().setCoverUpdatedOn(now);
            book.setBookCoverHash(BookCoverUtils.generateCoverHash());
        }

        log.info("{} regenerated cover regeneration for book ID {} ({}) finished with success={}", progress, book.getId(), title, success);
        if (!success) {
            throw ApiError.FAILED_TO_REGENERATE_COVER.createException();
        }
    }

    private void regenerateCoverForBookId(BookRegenerationInfo bookInfo) {
        bookRepository.findById(bookInfo.id()).ifPresent(book -> {
            BookFileProcessor processor = processorRegistry.getProcessorOrThrow(bookInfo.bookType());
            processor.generateCover(book);
        });
    }

    private void validateCoverFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiError.INVALID_INPUT.createException("Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.toLowerCase().startsWith("image/jpeg") && !contentType.toLowerCase().startsWith("image/png"))) {
            throw ApiError.INVALID_INPUT.createException("Only JPEG and PNG files are allowed");
        }
        long maxFileSize = 5L * 1024 * 1024;
        if (file.getSize() > maxFileSize) {
            throw ApiError.FILE_TOO_LARGE.createException(5);
        }
    }

    private byte[] extractBytesFromMultipartFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            log.error("Failed to read cover file: {}", e.getMessage());
            throw new RuntimeException("Failed to read cover file", e);
        }
    }

    private List<BookCoverInfo> getUnlockedBookCoverInfos(Set<Long> bookIds) {
        return bookQueryService.findAllWithMetadataByIds(bookIds).stream()
                .filter(book -> !isCoverLocked(book))
                .map(book -> new BookCoverInfo(book.getId(), book.getMetadata().getTitle()))
                .toList();
    }

    private List<BookRegenerationInfo> getUnlockedBookRegenerationInfos(Set<Long> bookIds) {
        return bookQueryService.findAllWithMetadataByIds(bookIds).stream()
                .filter(book -> !isCoverLocked(book))
                .map(book -> new BookRegenerationInfo(book.getId(), book.getMetadata().getTitle(), book.getBookType()))
                .toList();
    }

    private boolean isCoverLocked(BookEntity book) {
        return book.getMetadata().getCoverLocked() != null && book.getMetadata().getCoverLocked();
    }

    private String getAuthorNames(BookEntity bookEntity) {
        if (bookEntity.getMetadata().getAuthors() != null && !bookEntity.getMetadata().getAuthors().isEmpty()) {
            return bookEntity.getMetadata().getAuthors().stream()
                    .map(AuthorEntity::getName)
                    .collect(Collectors.joining(", "));
        }
        return null;
    }

    private void updateBookCoverMetadata(BookEntity bookEntity) {
        Instant now = Instant.now();
        bookEntity.setMetadataUpdatedAt(now);
        bookEntity.getMetadata().setCoverUpdatedOn(now);
        bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
    }

    private void notifyBookCoverUpdate(BookEntity bookEntity) {
        notificationService.sendMessage(Topic.BOOKS_COVER_UPDATE, List.of(Map.of(
                "id", bookEntity.getId(),
                "coverUpdatedOn", bookEntity.getMetadata().getCoverUpdatedOn()
        )));
    }

    private void notifyBulkCoverUpdate(List<Long> refreshedIds) {
        if (refreshedIds.isEmpty()) {
            return;
        }

        List<Map<String, Object>> refreshedPatches = transactionTemplate.execute(status -> {
            List<BookEntity> entities = bookQueryService.findAllWithMetadataByIds(new HashSet<>(refreshedIds));
            if (entities == null || entities.isEmpty()) {
                return List.<Map<String, Object>>of();
            }

            entities.forEach(e -> {
                if (e.getMetadata() != null) {
                    e.getMetadata().getCoverUpdatedOn();
                }
            });

            return entities.stream()
                    .map(e -> Map.<String, Object>of(
                            "id", e.getId(),
                            "coverUpdatedOn", e.getMetadata() == null ? null : e.getMetadata().getCoverUpdatedOn()
                    ))
                    .toList();
        });

        if (refreshedPatches != null && !refreshedPatches.isEmpty()) {
            notificationService.sendMessage(Topic.BOOKS_COVER_UPDATE, refreshedPatches);
        }
    }

    private void pauseAfterBatchIfNeeded(int current, int total) {
        if (current % BATCH_SIZE == 0 && current < total) {
            try {
                log.info("Processed {} items, pausing briefly before next batch...", current);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Batch pause interrupted");
            }
        }
    }
}
