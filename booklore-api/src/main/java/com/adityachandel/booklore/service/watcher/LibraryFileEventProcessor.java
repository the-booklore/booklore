package com.adityachandel.booklore.service.watcher;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.PermissionType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@AllArgsConstructor
public class LibraryFileEventProcessor {

    private static final long DEBOUNCE_MS = 500L;
    private static final long FOLDER_CREATE_DEBOUNCE_MS = 2000L; // Longer debounce for folder creates to allow file copying
    private static final int MIN_AUDIO_FILES_FOR_FOLDER_AUDIOBOOK = 2;

    private final BlockingQueue<FileEvent> eventQueue = new LinkedBlockingQueue<>();
    private final LibraryRepository libraryRepository;
    private final BookFileTransactionalHandler bookFileTransactionalHandler;
    private final BookFilePersistenceService bookFilePersistenceService;
    private final NotificationService notificationService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ConcurrentMap<Path, ScheduledFuture<?>> pendingDeletes = new ConcurrentHashMap<>();
    private final ConcurrentMap<Path, ScheduledFuture<?>> pendingFolderCreates = new ConcurrentHashMap<>();
    private final Set<Path> filesFromPendingFolder = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        Thread.ofVirtual().start(() -> {
            log.info("LibraryFileEventProcessor virtual thread started.");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    handleEvent(eventQueue.take());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("LibraryFileEventProcessor virtual thread interrupted.");
                } catch (Exception e) {
                    log.error("Error while processing file event", e);
                }
            }
        });
    }

    public void processFile(WatchEvent.Kind<?> eventKind, long libraryId, String libraryPath, String filePath) {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        boolean isDirectory = Files.isDirectory(path);

        if (eventKind == StandardWatchEventKinds.ENTRY_DELETE) {
            // Schedule DELETE after debounce
            ScheduledFuture<?> existing = pendingDeletes.put(path, scheduler.schedule(() -> {
                eventQueue.offer(new FileEvent(eventKind, libraryId, libraryPath, filePath, false));
                pendingDeletes.remove(path);
            }, DEBOUNCE_MS, TimeUnit.MILLISECONDS));

            if (existing != null) existing.cancel(false);
        } else if (eventKind == StandardWatchEventKinds.ENTRY_CREATE) {
            // If a DELETE is pending for this path, cancel both DELETE and CREATE
            ScheduledFuture<?> pendingDelete = pendingDeletes.remove(path);
            if (pendingDelete != null) {
                pendingDelete.cancel(false);
                log.debug("[DEBOUNCE] CREATE ignored because pending DELETE exists for '{}'", path);
                return;
            }

            if (isDirectory) {
                // Debounce folder creates to allow files to be copied first
                log.debug("[DEBOUNCE] Scheduling folder create for '{}' with {}ms delay", path, FOLDER_CREATE_DEBOUNCE_MS);
                ScheduledFuture<?> existingFolder = pendingFolderCreates.put(path, scheduler.schedule(() -> {
                    eventQueue.offer(new FileEvent(eventKind, libraryId, libraryPath, filePath, true));
                    pendingFolderCreates.remove(path);
                    // Clear tracked files for this folder after processing
                    filesFromPendingFolder.removeIf(f -> f.startsWith(path));
                }, FOLDER_CREATE_DEBOUNCE_MS, TimeUnit.MILLISECONDS));

                if (existingFolder != null) existingFolder.cancel(false);
            } else {
                // Check if this file is inside a pending folder
                boolean insidePendingFolder = pendingFolderCreates.keySet().stream().anyMatch(path::startsWith);
                if (insidePendingFolder) {
                    // Track this file but don't process it yet - the folder handler will decide
                    filesFromPendingFolder.add(path);
                    log.debug("[DEBOUNCE] File '{}' tracked as part of pending folder", path.getFileName());
                } else {
                    // Process file immediately
                    eventQueue.offer(new FileEvent(eventKind, libraryId, libraryPath, filePath, false));
                }
            }
        } else {
            // Other events
            eventQueue.offer(new FileEvent(eventKind, libraryId, libraryPath, filePath, false));
        }
    }

    private void handleEvent(FileEvent event) {
        Path path = Paths.get(event.filePath()).toAbsolutePath().normalize();
        String fileName = path.getFileName().toString();
        log.info("[PROCESS] '{}' event for '{}'{}", event.eventKind().name(), fileName,
                event.isDebouncedFolder() ? " (debounced folder)" : "");

        LibraryEntity library = libraryRepository.findById(event.libraryId())
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(event.libraryId()));

        if (library.getLibraryPaths().stream().noneMatch(lp -> path.startsWith(lp.getPath()))) {
            log.warn("[SKIP] Path outside of library: '{}'", path);
            return;
        }

        // Use isDebouncedFolder flag for debounced events, otherwise fall back to heuristic
        boolean isDirectory = event.isDebouncedFolder() || isFolder(path);

        if (isDirectory) {
            switch (event.eventKind().name()) {
                case "ENTRY_CREATE" -> handleFolderCreate(library, path);
                case "ENTRY_DELETE" -> handleFolderDelete(library, path);
                default -> log.warn("[SKIP] Folder event '{}' ignored for '{}'", event.eventKind().name(), fileName);
            }
            return;
        }

        if (!isBookFile(fileName)) {
            log.debug("[SKIP] Ignored non-book file '{}'", fileName);
            return;
        }

        switch (event.eventKind().name()) {
            case "ENTRY_CREATE" -> handleFileCreate(library, path);
            case "ENTRY_DELETE" -> handleFileDelete(library, path);
            default -> log.debug("[SKIP] File event '{}' ignored for '{}'", event.eventKind().name(), fileName);
        }
    }

    private void handleFileCreate(LibraryEntity library, Path path) {
        log.info("[FILE_CREATE] '{}'", path);
        bookFileTransactionalHandler.handleNewBookFile(library.getId(), path);
    }

    private void handleFileDelete(LibraryEntity library, Path path) {
        log.info("[FILE_DELETE] '{}'", path);
        try {
            String libPath = bookFilePersistenceService.findMatchingLibraryPath(library, path);
            LibraryPathEntity libPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(library, libPath);

            Path relPath = Paths.get(libPathEntity.getPath()).relativize(path);
            String fileName = relPath.getFileName().toString();
            String fileSubPath = Optional.ofNullable(relPath.getParent()).map(Path::toString).orElse("");

            bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(libPathEntity.getId(), fileSubPath, fileName)
                    .ifPresentOrElse(bookFile -> {
                        var book = bookFile.getBook();
                        var remainingFiles = bookFilePersistenceService.countBookFilesByBookId(book.getId());

                        if (remainingFiles <= 1) {
                            // Last file - mark book as deleted
                            bookFilePersistenceService.markBookAsDeleted(book);
                            notificationService.sendMessageToPermissions(Topic.BOOKS_REMOVE, Set.of(book.getId()),
                                    Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY));
                            log.info("[MARKED_DELETED] Book '{}' marked as deleted (last file removed)", fileName);
                        } else {
                            // Multiple files - just delete this file
                            bookFilePersistenceService.deleteBookFile(bookFile);
                            notificationService.sendMessageToPermissions(Topic.BOOK_UPDATE, Set.of(book.getId()),
                                    Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY));
                            log.info("[FILE_REMOVED] BookFile '{}' removed from book (id={}), {} files remaining",
                                    fileName, book.getId(), remainingFiles - 1);
                        }
                    }, () -> log.warn("[NOT_FOUND] BookFile for deleted path '{}' not found", path));

        } catch (Exception e) {
            log.warn("[ERROR] While handling file delete '{}': {}", path, e.getMessage());
        }
    }

    private void handleFolderCreate(LibraryEntity library, Path folderPath) {
        log.info("[FOLDER_CREATE] '{}'", folderPath);

        // Check if folder is a folder-based audiobook (2+ audio files, no ebook files)
        FolderAnalysis analysis = analyzeFolderForAudiobook(folderPath);

        if (analysis.isFolderBasedAudiobook()) {
            log.info("[FOLDER_AUDIOBOOK] Detected folder-based audiobook: {} ({} audio files)",
                    folderPath.getFileName(), analysis.audioFileCount());
            try {
                bookFileTransactionalHandler.handleNewFolderAudiobook(library.getId(), folderPath);
                // Clear any tracked files inside this folder - they're now part of the folder audiobook
                int cleared = 0;
                var iterator = filesFromPendingFolder.iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().startsWith(folderPath)) {
                        iterator.remove();
                        cleared++;
                    }
                }
                if (cleared > 0) {
                    log.debug("[FOLDER_AUDIOBOOK] Cleared {} tracked files that are now part of folder audiobook", cleared);
                }
            } catch (Exception e) {
                log.warn("[ERROR] Processing folder audiobook '{}': {}", folderPath, e.getMessage());
            }
        } else {
            // Not a folder-based audiobook - process tracked files individually
            var trackedFiles = filesFromPendingFolder.stream()
                    .filter(p -> p.startsWith(folderPath))
                    .filter(p -> isBookFile(p.getFileName().toString()))
                    .toList();

            if (!trackedFiles.isEmpty()) {
                log.info("[FOLDER_CREATE] Processing {} tracked files individually", trackedFiles.size());
                for (Path filePath : trackedFiles) {
                    try {
                        bookFileTransactionalHandler.handleNewBookFile(library.getId(), filePath);
                    } catch (Exception e) {
                        log.warn("[ERROR] Processing tracked file '{}': {}", filePath, e.getMessage());
                    }
                    filesFromPendingFolder.remove(filePath);
                }
            }

            // Also walk the folder for any files that weren't tracked (e.g., existing files)
            try (var stream = Files.walk(folderPath)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> isBookFile(p.getFileName().toString()))
                        .filter(p -> !trackedFiles.contains(p)) // Skip already processed tracked files
                        .forEach(p -> {
                            try {
                                bookFileTransactionalHandler.handleNewBookFile(library.getId(), p);
                            } catch (Exception e) {
                                log.warn("[ERROR] Processing file '{}': {}", p, e.getMessage());
                            }
                        });
            } catch (IOException e) {
                log.warn("[ERROR] Walking folder '{}': {}", folderPath, e.getMessage());
            }
        }
    }

    private FolderAnalysis analyzeFolderForAudiobook(Path folderPath) {
        int audioFileCount = 0;
        boolean hasNonAudioBook = false;

        try (var stream = Files.walk(folderPath)) {
            var files = stream.filter(Files::isRegularFile).toList();

            for (Path file : files) {
                String fileName = file.getFileName().toString();
                var ext = BookFileExtension.fromFileName(fileName);
                if (ext.isPresent()) {
                    if (ext.get().getType() == BookFileType.AUDIOBOOK) {
                        audioFileCount++;
                    } else {
                        hasNonAudioBook = true;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[ERROR] Analyzing folder '{}': {}", folderPath, e.getMessage());
        }

        return new FolderAnalysis(audioFileCount, hasNonAudioBook);
    }

    private record FolderAnalysis(int audioFileCount, boolean hasNonAudioBook) {
        boolean isFolderBasedAudiobook() {
            return audioFileCount >= MIN_AUDIO_FILES_FOR_FOLDER_AUDIOBOOK && !hasNonAudioBook;
        }
    }

    private void handleFolderDelete(LibraryEntity library, Path folderPath) {
        log.info("[FOLDER_DELETE] '{}'", folderPath);
        try {
            String libPath = bookFilePersistenceService.findMatchingLibraryPath(library, folderPath);
            LibraryPathEntity libPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(library, libPath);

            String relativePrefix = FileUtils.getRelativeSubPath(libPathEntity.getPath(), folderPath);
            int count = bookFilePersistenceService.markAllBooksUnderPathAsDeleted(libPathEntity.getId(), relativePrefix);
            log.info("[MARKED_DELETED] {} books under '{}'", count, folderPath);
        } catch (Exception e) {
            log.warn("[ERROR] Folder delete '{}': {}", folderPath, e.getMessage());
        }
    }

    private boolean isFolder(Path path) {
        return !path.getFileName().toString().contains(".");
    }

    private boolean isBookFile(String fileName) {
        return BookFileExtension.fromFileName(fileName).isPresent();
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        log.info("Shutting down LibraryFileEventProcessor...");
    }

    public record FileEvent(WatchEvent.Kind<?> eventKind, long libraryId, String libraryPath, String filePath, boolean isDebouncedFolder) {
    }
}