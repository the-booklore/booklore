package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class UnifiedFileMoveService {

    private final FileMovingHelper fileMovingHelper;
    private final MonitoredFileOperationService monitoredFileOperationService;
    private final MonitoringRegistrationService monitoringRegistrationService;

    /**
     * Moves a single book file to match the library's file naming pattern.
     * Used for metadata updates where one file needs to be moved.
     */
    public void moveSingleBookFile(BookEntity bookEntity) {
        if (bookEntity.getLibraryPath() == null || bookEntity.getLibraryPath().getLibrary() == null) {
            log.debug("Book ID {} has no library associated. Skipping file move.", bookEntity.getId());
            return;
        }

        String pattern = fileMovingHelper.getFileNamingPattern(bookEntity.getLibraryPath().getLibrary());

        if (!fileMovingHelper.hasRequiredPathComponents(bookEntity)) {
            log.debug("Missing required path components for book ID {}. Skipping file move.", bookEntity.getId());
            return;
        }

        Path currentFilePath = bookEntity.getFullFilePath();
        if (!Files.exists(currentFilePath)) {
            log.warn("File does not exist for book ID {}: {}. Skipping file move.", bookEntity.getId(), currentFilePath);
            return;
        }

        // Check if current path differs from expected pattern
        Path expectedFilePath = fileMovingHelper.generateNewFilePath(bookEntity, pattern);
        if (currentFilePath.equals(expectedFilePath)) {
            log.debug("File for book ID {} is already in the correct location according to library pattern. No move needed.", bookEntity.getId());
            return;
        }

        log.info("File for book ID {} needs to be moved from {} to {} to match library pattern", bookEntity.getId(), currentFilePath, expectedFilePath);

        // For single file moves, use targeted path protection
        monitoredFileOperationService.executeWithMonitoringSuspended(currentFilePath, expectedFilePath, bookEntity.getLibraryPath().getLibrary().getId(), () -> {
            try {
                boolean moved = fileMovingHelper.moveBookFileIfNeeded(bookEntity, pattern);
                if (moved) {
                    log.info("Successfully moved file for book ID {} from {} to {} to match library pattern", bookEntity.getId(), currentFilePath, bookEntity.getFullFilePath());
                }
                return moved;
            } catch (IOException e) {
                log.error("Failed to move file for book ID {}: {}", bookEntity.getId(), e.getMessage(), e);
                throw new RuntimeException("File move failed", e);
            }
        });
    }

    /**
     * Moves multiple book files in batches with library-level monitoring protection.
     * Used for bulk file operations where many files need to be moved.
     */
    public void moveBatchBookFiles(List<BookEntity> books, BatchMoveCallback callback) {
        if (books.isEmpty()) {
            log.debug("No books to move");
            return;
        }

        Set<Long> libraryIds = new HashSet<>();
        Map<Long, Set<Path>> libraryToRootsMap = new HashMap<>();

        // Collect library information for monitoring protection
        for (BookEntity book : books) {
            if (book.getMetadata() == null) continue;
            if (!fileMovingHelper.hasRequiredPathComponents(book)) continue;

            Path oldFilePath = book.getFullFilePath();
            if (!Files.exists(oldFilePath)) continue;

            Long libraryId = book.getLibraryPath().getLibrary().getId();
            Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();

            libraryToRootsMap.computeIfAbsent(libraryId, k -> new HashSet<>()).add(libraryRoot);
            libraryIds.add(libraryId);
        }

        // Unregister libraries for batch operation
        unregisterLibrariesBatch(libraryToRootsMap);

        try {
            // Process each book
            for (BookEntity book : books) {
                if (book.getMetadata() == null) continue;

                String pattern = fileMovingHelper.getFileNamingPattern(book.getLibraryPath().getLibrary());

                if (!fileMovingHelper.hasRequiredPathComponents(book)) continue;

                Path oldFilePath = book.getFullFilePath();
                if (!Files.exists(oldFilePath)) {
                    log.warn("File not found for book {}: {}", book.getId(), oldFilePath);
                    continue;
                }

                log.debug("Moving book {}: '{}'", book.getId(), book.getMetadata().getTitle());

                try {
                    boolean moved = fileMovingHelper.moveBookFileIfNeeded(book, pattern);
                    if (moved) {
                        log.debug("Book {} moved successfully", book.getId());
                        callback.onBookMoved(book);
                    }

                    // Move additional files if any
                    if (book.getAdditionalFiles() != null && !book.getAdditionalFiles().isEmpty()) {
                        fileMovingHelper.moveAdditionalFiles(book, pattern);
                    }
                } catch (IOException e) {
                    log.error("Move failed for book {}: {}", book.getId(), e.getMessage(), e);
                    callback.onBookMoveFailed(book, e);
                }
            }

            // Small delay to let filesystem operations settle
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted during batch move delay");
            }

        } finally {
            // Re-register libraries
            registerLibrariesBatch(libraryToRootsMap);
        }
    }

    private void unregisterLibrariesBatch(Map<Long, Set<Path>> libraryToRootsMap) {
        log.debug("Unregistering {} libraries for batch move", libraryToRootsMap.size());

        for (Map.Entry<Long, Set<Path>> entry : libraryToRootsMap.entrySet()) {
            Long libraryId = entry.getKey();
            monitoringRegistrationService.unregisterLibrary(libraryId);
            log.debug("Unregistered library {}", libraryId);
        }
    }

    private void registerLibrariesBatch(Map<Long, Set<Path>> libraryToRootsMap) {
        log.debug("Re-registering {} libraries after batch move", libraryToRootsMap.size());

        for (Map.Entry<Long, Set<Path>> entry : libraryToRootsMap.entrySet()) {
            Long libraryId = entry.getKey();
            Set<Path> libraryRoots = entry.getValue();

            for (Path libraryRoot : libraryRoots) {
                if (Files.exists(libraryRoot) && Files.isDirectory(libraryRoot)) {
                    monitoringRegistrationService.registerLibraryPaths(libraryId, libraryRoot);
                    log.debug("Re-registered library {} at {}", libraryId, libraryRoot);
                }
            }
        }
    }

    /**
     * Callback interface for batch move operations
     */
    public interface BatchMoveCallback {
        void onBookMoved(BookEntity book);
        void onBookMoveFailed(BookEntity book, Exception error);
    }
}

