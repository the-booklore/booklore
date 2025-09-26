package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.repository.LibraryRepository;
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
    private final LibraryRepository libraryRepository;

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
        Path expectedFilePath = fileMovingHelper.generateNewFilePath(bookEntity, pattern);

        // Check if current path differs from expected pattern
        if (currentFilePath.equals(expectedFilePath)) {
            log.debug("File for book ID {} is already in the correct location according to library pattern. No move needed.", bookEntity.getId());
            return;
        }

        log.info("File for book ID {} needs to be moved from {} to {} to match library pattern",
                bookEntity.getId(), currentFilePath, expectedFilePath);

        Long libraryId = bookEntity.getLibraryPath().getLibrary().getId();
        Path libraryRoot = Paths.get(bookEntity.getLibraryPath().getPath())
                .toAbsolutePath().normalize();

        // unregister the entire library to suppress all watch events during the move
        monitoringRegistrationService.unregisterLibrary(libraryId);

        try {
            monitoredFileOperationService.executeWithMonitoringSuspended(
                    currentFilePath, expectedFilePath, libraryId, () -> {
                        try {
                            boolean moved = fileMovingHelper.moveBookFileIfNeeded(bookEntity, pattern);
                            if (moved) {
                                log.info("Successfully moved file for book ID {} from {} to {}",
                                        bookEntity.getId(), currentFilePath, bookEntity.getFullFilePath());
                            }
                            return moved;
                        } catch (IOException e) {
                            log.error("Failed to move file for book ID {}: {}", bookEntity.getId(), e.getMessage(), e);
                            throw new RuntimeException("File move failed", e);
                        }
                    }
            );
        } finally {
            // re-register all folders under the library after the move
            monitoringRegistrationService.registerLibraryPaths(libraryId, libraryRoot);
        }
    }

    /**
     * Moves multiple book files in batches with cross-library support and library-level monitoring protection.
     * Used for bulk file operations where many files need to be moved, potentially across libraries.
     */
    public void moveBatchBookFiles(List<BookEntity> books, Map<Long, Long> bookToTargetLibraryMap, BatchMoveCallback callback) {
        if (books.isEmpty()) {
            log.debug("No books to move");
            return;
        }

        Set<Long> allLibraryIds = new HashSet<>();
        Map<Long, Set<Path>> libraryToRootsMap = new HashMap<>();

        // Collect library information for monitoring protection
        for (BookEntity book : books) {
            if (book.getMetadata() == null) continue;
            if (!fileMovingHelper.hasRequiredPathComponents(book)) continue;

            Path oldFilePath = book.getFullFilePath();
            if (!Files.exists(oldFilePath)) continue;

            // Source library
            Long sourceLibraryId = book.getLibraryPath().getLibrary().getId();
            Path sourceLibraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
            libraryToRootsMap.computeIfAbsent(sourceLibraryId, k -> new HashSet<>()).add(sourceLibraryRoot);
            allLibraryIds.add(sourceLibraryId);

            // Target library (if different)
            Long targetLibraryId = bookToTargetLibraryMap.get(book.getId());
            if (targetLibraryId != null && !targetLibraryId.equals(sourceLibraryId)) {
                LibraryEntity targetLibrary = libraryRepository.findById(targetLibraryId).orElse(null);
                if (targetLibrary != null && targetLibrary.getLibraryPaths() != null && !targetLibrary.getLibraryPaths().isEmpty()) {
                    Path targetLibraryRoot = Paths.get(targetLibrary.getLibraryPaths().getFirst().getPath()).toAbsolutePath().normalize();
                    libraryToRootsMap.computeIfAbsent(targetLibraryId, k -> new HashSet<>()).add(targetLibraryRoot);
                    allLibraryIds.add(targetLibraryId);
                }
            }
        }

        // Unregister all affected libraries for batch operation
        unregisterLibrariesBatch(libraryToRootsMap);

        try {
            // Small delay to let any pending file watcher events settle
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted during pre-move delay");
            }

            // Process each book
            for (BookEntity book : books) {
                if (book.getMetadata() == null) continue;
                if (!fileMovingHelper.hasRequiredPathComponents(book)) continue;

                Path oldFilePath = book.getFullFilePath();
                if (!Files.exists(oldFilePath)) {
                    log.warn("File not found for book {}: {}", book.getId(), oldFilePath);
                    continue;
                }

                log.debug("Moving book {}: '{}'", book.getId(), book.getMetadata().getTitle());

                try {
                    Long targetLibraryId = bookToTargetLibraryMap.get(book.getId());
                    boolean moved = false;

                    if (targetLibraryId != null && !targetLibraryId.equals(book.getLibraryPath().getLibrary().getId())) {
                        // Cross-library move
                        LibraryEntity targetLibrary = libraryRepository.findById(targetLibraryId).orElse(null);
                        if (targetLibrary != null) {
                            moved = fileMovingHelper.moveBookFileToLibrary(book, targetLibrary);
                            if (moved) {
                                log.debug("Book {} moved to library {} successfully", book.getId(), targetLibraryId);
                            }
                        } else {
                            log.error("Target library {} not found for book {}", targetLibraryId, book.getId());
                            callback.onBookMoveFailed(book, new RuntimeException("Target library not found"));
                            continue;
                        }
                    } else {
                        // Same library move (existing functionality)
                        String pattern = fileMovingHelper.getFileNamingPattern(book.getLibraryPath().getLibrary());
                        moved = fileMovingHelper.moveBookFileIfNeeded(book, pattern);
                        if (moved) {
                            log.debug("Book {} moved within library successfully", book.getId());
                        }
                    }

                    if (moved) {
                        callback.onBookMoved(book);

                        // Move additional files if any
                        if (book.getAdditionalFiles() != null && !book.getAdditionalFiles().isEmpty()) {
                            String pattern = fileMovingHelper.getFileNamingPattern(book.getLibraryPath().getLibrary());
                            fileMovingHelper.moveAdditionalFiles(book, pattern);
                        }
                    }
                } catch (IOException e) {
                    log.error("Move failed for book {}: {}", book.getId(), e.getMessage(), e);
                    callback.onBookMoveFailed(book, e);
                }
            }

            // Longer delay to let filesystem operations settle before re-registering monitoring
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted during batch move delay");
            }

        } finally {
            // Re-register all affected libraries
            registerLibrariesBatch(libraryToRootsMap);
        }
    }

    // Overloaded method for backward compatibility with same-library moves
    public void moveBatchBookFiles(List<BookEntity> books, BatchMoveCallback callback) {
        moveBatchBookFiles(books, new HashMap<>(), callback);
    }

    private void unregisterLibrariesBatch(Map<Long, Set<Path>> libraryToRootsMap) {
        log.debug("Unregistering {} libraries for batch move", libraryToRootsMap.size());

        for (Map.Entry<Long, Set<Path>> entry : libraryToRootsMap.entrySet()) {
            Long libraryId = entry.getKey();
            try {
                monitoringRegistrationService.unregisterLibrary(libraryId);
                log.debug("Unregistered library {}", libraryId);
            } catch (Exception e) {
                log.warn("Failed to unregister library {}: {}", libraryId, e.getMessage());
            }
        }
    }

    private void registerLibrariesBatch(Map<Long, Set<Path>> libraryToRootsMap) {
        log.debug("Re-registering {} libraries after batch move", libraryToRootsMap.size());

        for (Map.Entry<Long, Set<Path>> entry : libraryToRootsMap.entrySet()) {
            Long libraryId = entry.getKey();
            Set<Path> libraryRoots = entry.getValue();

            // Verify library still exists before re-registering
            try {
                LibraryEntity library = libraryRepository.findById(libraryId).orElse(null);
                if (library == null) {
                    log.warn("Library {} no longer exists, skipping re-registration", libraryId);
                    continue;
                }

                for (Path libraryRoot : libraryRoots) {
                    if (Files.exists(libraryRoot) && Files.isDirectory(libraryRoot)) {
                        try {
                            monitoringRegistrationService.registerLibraryPaths(libraryId, libraryRoot);
                            log.debug("Re-registered library {} at {}", libraryId, libraryRoot);
                        } catch (Exception e) {
                            log.warn("Failed to re-register library {} at {}: {}", libraryId, libraryRoot, e.getMessage());
                        }
                    } else {
                        log.debug("Library root {} no longer exists or is not a directory, skipping re-registration", libraryRoot);
                    }
                }
            } catch (Exception e) {
                log.error("Error verifying library {} during re-registration: {}", libraryId, e.getMessage());
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