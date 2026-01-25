package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.websocket.LogNotification;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.projection.BookCoverUpdateProjection;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BookCoverService {

    private static final int BATCH_SIZE = 100;

    private final BookRepository bookRepository;
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

    private record BookRegenerationInfo(Long id, String title, BookFileType bookType, boolean coverLocked) {
    }

    // =========================
    // SECTION: COVER UPDATES
    // =========================

    /**
     * Generate a custom cover for a single book.
     */
    public void generateCustomCover(long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (isCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        String title = bookEntity.getMetadata().getTitle();
        String author = getAuthorNames(bookEntity);
        byte[] coverBytes = coverImageGenerator.generateCover(title, author);

        fileService.createThumbnailFromBytes(bookId, coverBytes);
        writeCoverToBookFile(bookEntity, (writer, book) -> writer.replaceCoverImageFromBytes(book, coverBytes));
        updateBookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
    }

    /**
     * Update cover image from uploaded file for a single book.
     */
    @Transactional
    public void updateCoverFromFile(Long bookId, MultipartFile file) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (isCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        fileService.createThumbnailFromFile(bookId, file);
        writeCoverToBookFile(bookEntity, (writer, book) -> writer.replaceCoverImageFromUpload(book, file));
        updateBookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
    }

    /**
     * Update cover image from a URL for a single book.
     */
    @Transactional
    public void updateCoverFromUrl(Long bookId, String url) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (isCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        fileService.createThumbnailFromUrl(bookId, url);
        writeCoverToBookFile(bookEntity, (writer, book) -> writer.replaceCoverImageFromUrl(book, url));
        updateBookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
    }

    /**
     * Bulk update cover images from a file for multiple books.
     */
    public void updateCoverFromFileForBooks(Set<Long> bookIds, MultipartFile file) {
        validateCoverFile(file);
        byte[] coverImageBytes = extractBytesFromMultipartFile(file);
        List<BookCoverInfo> unlockedBooks = getUnlockedBookCoverInfos(bookIds);
        SecurityContextVirtualThread.runWithSecurityContext(() -> processBulkCoverUpdate(unlockedBooks, coverImageBytes));
    }

    // =========================
    // SECTION: COVER REGENERATION
    // =========================

    /**
     * Regenerate cover for a single book.
     */
    public void regenerateCover(long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (isCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }
        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(bookEntity.getPrimaryBookFile().getBookType());
        boolean success = processor.generateCover(bookEntity);
        if (!success) {
            throw ApiError.FAILED_TO_REGENERATE_COVER.createException();
        }
        updateBookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
    }

    /**
     * Regenerate covers for a set of books.
     */
    public void regenerateCoversForBooks(Set<Long> bookIds) {
        List<BookRegenerationInfo> unlockedBooks = getUnlockedBookRegenerationInfos(bookIds);
        SecurityContextVirtualThread.runWithSecurityContext(() -> processBulkCoverRegeneration(unlockedBooks));
    }

    /**
     * Generate custom covers for a set of books.
     */
    public void generateCustomCoversForBooks(Set<Long> bookIds) {
        List<BookCoverInfo> unlockedBooks = getUnlockedBookCoverInfos(bookIds);
        SecurityContextVirtualThread.runWithSecurityContext(() -> processBulkCustomCoverGeneration(unlockedBooks));
    }

    /**
     * Regenerate covers for all books.
     */
    public void regenerateCovers() {
        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
                List<BookRegenerationInfo> books = bookQueryService.getAllFullBookEntities().stream()
                        .filter(book -> !isCoverLocked(book))
                        .map(book -> new BookRegenerationInfo(book.getId(), book.getMetadata().getTitle(), book.getPrimaryBookFile().getBookType(), false))
                        .toList();
                int total = books.size();
                notificationService.sendMessage(Topic.LOG, LogNotification.info("Started regenerating covers for " + total + " books"));

                int current = 1;
                List<Long> refreshedIds = new ArrayList<>();

                for (BookRegenerationInfo bookInfo : books) {
                    try {
                        String progress = "(" + current + "/" + total + ") ";
                        notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Regenerating cover for: " + bookInfo.title()));

                        transactionTemplate.execute(status -> {
                            bookRepository.findById(bookInfo.id()).ifPresent(book -> {
                                BookFileProcessor processor = processorRegistry.getProcessorOrThrow(book.getPrimaryBookFile().getBookType());
                                boolean success = processor.generateCover(book);

                                if (success) {
                                    updateBookCoverMetadata(book);
                                    bookRepository.save(book);
                                    refreshedIds.add(book.getId());
                                    log.info("{}Successfully regenerated cover for book ID {} ({})", progress, book.getId(), bookInfo.title());
                                } else {
                                    log.warn("{}Failed to regenerate cover for book ID {} ({})", progress, book.getId(), bookInfo.title());
                                }
                            });
                            return null;
                        });
                    } catch (Exception e) {
                        log.error("Failed to regenerate cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                    }
                    current++;
                }

                notifyBulkCoverUpdate(refreshedIds);
                notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished regenerating covers"));
            } catch (Exception e) {
                log.error("Error during cover regeneration: {}", e.getMessage(), e);
                notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during cover regeneration"));
            }
        });
    }

    // =========================
    // SECTION: BULK OPERATIONS
    // =========================

    private void processBulkCoverUpdate(List<BookCoverInfo> books, byte[] coverImageBytes) {
        try {
            int total = books.size();
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Started updating covers for " + total + " selected book(s)"));

            int current = 1;
            List<Long> refreshedIds = new ArrayList<>();

            for (BookCoverInfo bookInfo : books) {
                try {
                    String progress = "(" + current + "/" + total + ") ";
                    notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Updating cover for: " + bookInfo.title()));

                    transactionTemplate.execute(status -> {
                        bookRepository.findById(bookInfo.id()).ifPresent(book -> {
                            fileService.createThumbnailFromBytes(bookInfo.id(), coverImageBytes);
                            writeCoverToBookFile(book, (writer, b) -> writer.replaceCoverImageFromBytes(b, coverImageBytes));
                            updateBookCoverMetadata(book);
                            bookRepository.save(book);
                            refreshedIds.add(book.getId());
                        });
                        return null;
                    });

                    log.info("{}Successfully updated cover for book ID {} ({})", progress, bookInfo.id(), bookInfo.title());
                } catch (Exception e) {
                    log.error("Failed to update cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                }
                current++;
            }

            notifyBulkCoverUpdate(refreshedIds);
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

                    transactionTemplate.execute(status -> {
                        bookRepository.findById(bookInfo.id()).ifPresent(book -> {
                            BookFileProcessor processor = processorRegistry.getProcessorOrThrow(bookInfo.bookType());
                            boolean success = processor.generateCover(book);

                            if (success) {
                                updateBookCoverMetadata(book);
                                bookRepository.save(book);
                                refreshedIds.add(book.getId());
                            }
                        });
                        return null;
                    });

                    log.info("{}Successfully regenerated cover for book ID {} ({})", progress, bookInfo.id(), bookInfo.title());
                } catch (Exception e) {
                    log.error("Failed to regenerate cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                }
                current++;
            }

            notifyBulkCoverUpdate(refreshedIds);
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished regenerating covers for selected books"));
        } catch (Exception e) {
            log.error("Error during cover regeneration: {}", e.getMessage(), e);
            notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during cover regeneration"));
        }
    }

    private void processBulkCustomCoverGeneration(List<BookCoverInfo> books) {
        try {
            int total = books.size();
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Started generating custom covers for " + total + " selected book(s)"));

            int current = 1;
            List<Long> refreshedIds = new ArrayList<>();

            for (BookCoverInfo bookInfo : books) {
                try {
                    String progress = "(" + current + "/" + total + ") ";
                    notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Generating custom cover for: " + bookInfo.title()));

                    transactionTemplate.execute(status -> {
                        bookRepository.findById(bookInfo.id()).ifPresent(book -> {
                            String title = book.getMetadata().getTitle();
                            String author = getAuthorNames(book);
                            byte[] coverBytes = coverImageGenerator.generateCover(title, author);

                            fileService.createThumbnailFromBytes(book.getId(), coverBytes);
                            writeCoverToBookFile(book, (writer, b) -> writer.replaceCoverImageFromBytes(b, coverBytes));
                            updateBookCoverMetadata(book);
                            bookRepository.save(book);
                            refreshedIds.add(book.getId());
                        });
                        return null;
                    });

                    log.info("{}Successfully generated custom cover for book ID {} ({})", progress, bookInfo.id(), bookInfo.title());
                } catch (Exception e) {
                    log.error("Failed to generate custom cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                }
                current++;
            }

            notifyBulkCoverUpdate(refreshedIds);
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished generating custom covers for selected books"));
        } catch (Exception e) {
            log.error("Error during custom cover generation: {}", e.getMessage(), e);
            notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during custom cover generation"));
        }
    }

    // =========================
    // SECTION: INTERNAL HELPERS
    // =========================

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
                .map(book -> new BookRegenerationInfo(book.getId(), book.getMetadata().getTitle(), book.getPrimaryBookFile().getBookType(), false))
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

    private void writeCoverToBookFile(BookEntity bookEntity, BiConsumer<MetadataWriter, BookEntity> writerAction) {
        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        boolean convertCbrCb7ToCbz = settings.isConvertCbrCb7ToCbz();

        if ((bookEntity.getPrimaryBookFile().getBookType() != BookFileType.CBX || convertCbrCb7ToCbz)) {
            metadataWriterFactory.getWriter(bookEntity.getPrimaryBookFile().getBookType())
                    .ifPresent(writer -> {
                        writerAction.accept(writer, bookEntity);
                        String newHash = FileFingerprint.generateHash(bookEntity.getFullFilePath());
                        bookEntity.getPrimaryBookFile().setCurrentHash(newHash);
                    });
        }
    }

    private void updateBookCoverMetadata(BookEntity bookEntity) {
        Instant now = Instant.now();
        bookEntity.setMetadataUpdatedAt(now);
        bookEntity.getMetadata().setCoverUpdatedOn(now);
        bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
    }

    private void notifyBookCoverUpdate(BookEntity bookEntity) {
        List<BookCoverUpdateProjection> updates = bookRepository.findCoverUpdateInfoByIds(List.of(bookEntity.getId()));
        if (!updates.isEmpty()) {
            notificationService.sendMessage(Topic.BOOKS_COVER_UPDATE, updates);
        }
    }

    private void notifyBulkCoverUpdate(List<Long> refreshedIds) {
        if (refreshedIds.isEmpty()) {
            return;
        }
        List<BookCoverUpdateProjection> updates = bookRepository.findCoverUpdateInfoByIds(refreshedIds);
        if (!updates.isEmpty()) {
            notificationService.sendMessage(Topic.BOOKS_COVER_UPDATE, updates);
        }
    }
}
