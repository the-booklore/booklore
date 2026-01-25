package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.websocket.LogNotification;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.task.options.RescanLibraryContext;
import com.adityachandel.booklore.util.BookFileGroupingUtils;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class LibraryProcessingService {

    private final LibraryRepository libraryRepository;
    private final NotificationService notificationService;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final BookRepository bookRepository;
    private final FileAsBookProcessor fileAsBookProcessor;
    private final BookRestorationService bookRestorationService;
    private final BookDeletionService bookDeletionService;
    private final LibraryFileHelper libraryFileHelper;
    @PersistenceContext
    private final EntityManager entityManager;

    @Transactional
    public void processLibrary(long libraryId) {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        notificationService.sendMessage(Topic.LOG, LogNotification.info("Started processing library: " + libraryEntity.getName()));
        try {
            List<LibraryFile> libraryFiles = libraryFileHelper.getLibraryFiles(libraryEntity);
            List<LibraryFile> newFiles = detectNewBookPaths(libraryFiles, libraryEntity);
            fileAsBookProcessor.processLibraryFiles(newFiles, libraryEntity);
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished processing library: " + libraryEntity.getName()));
        } catch (IOException e) {
            log.error("Failed to process library {}: {}", libraryEntity.getName(), e.getMessage(), e);
            notificationService.sendMessage(Topic.LOG, LogNotification.error("Failed to process library: " + libraryEntity.getName() + " - " + e.getMessage()));
            throw new UncheckedIOException("Library processing failed", e);
        }
    }

    @Transactional
    public void rescanLibrary(RescanLibraryContext context) throws IOException {
        LibraryEntity libraryEntity = libraryRepository.findById(context.getLibraryId()).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(context.getLibraryId()));
        notificationService.sendMessage(Topic.LOG, LogNotification.info("Started refreshing library: " + libraryEntity.getName()));

        validateLibraryPathsAccessible(libraryEntity);

        List<LibraryFile> libraryFiles = libraryFileHelper.getLibraryFiles(libraryEntity);

        int existingBookCount = libraryEntity.getBookEntities().size();
        if (existingBookCount > 0 && libraryFiles.isEmpty()) {
            String paths = libraryEntity.getLibraryPaths().stream()
                    .map(p -> p.getPath())
                    .collect(Collectors.joining(", "));
            log.error("Library '{}' has {} existing books but scan found 0 files. Paths may be offline: {}",
                    libraryEntity.getName(), existingBookCount, paths);
            throw ApiError.LIBRARY_PATH_NOT_ACCESSIBLE.createException(paths);
        }

        List<Long> additionalFileIds = detectDeletedAdditionalFiles(libraryFiles, libraryEntity);
        if (!additionalFileIds.isEmpty()) {
            log.info("Detected {} removed additional files in library: {}", additionalFileIds.size(), libraryEntity.getName());
            bookDeletionService.deleteRemovedAdditionalFiles(additionalFileIds);
        }
        List<Long> bookIds = detectDeletedBookIds(libraryFiles, libraryEntity);
        if (!bookIds.isEmpty()) {
            log.info("Detected {} removed books in library: {}", bookIds.size(), libraryEntity.getName());
            bookDeletionService.processDeletedLibraryFiles(bookIds, libraryFiles);
        }
        bookRestorationService.restoreDeletedBooks(libraryFiles);
        entityManager.clear();

        List<LibraryFile> newFiles = detectNewBookPaths(libraryFiles, libraryEntity);

        // Cache results to avoid duplicate findMatchingBook() calls
        Map<LibraryFile, BookEntity> matchingBookMap = new HashMap<>();
        for (LibraryFile file : newFiles) {
            BookEntity match = findMatchingBook(file);
            if (match != null) {
                matchingBookMap.put(file, match);
            }
        }

        // Auto-attach files with matches
        for (Map.Entry<LibraryFile, BookEntity> entry : matchingBookMap.entrySet()) {
            autoAttachFile(entry.getValue(), entry.getKey());
        }

        // Process files without matches
        List<LibraryFile> toProcess = newFiles.stream()
                .filter(file -> !matchingBookMap.containsKey(file))
                .toList();

        fileAsBookProcessor.processLibraryFiles(toProcess, libraryEntity);

        notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished refreshing library: " + libraryEntity.getName()));
    }

    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        fileAsBookProcessor.processLibraryFiles(libraryFiles, libraryEntity);
    }

    private void validateLibraryPathsAccessible(LibraryEntity libraryEntity) {
        for (var pathEntity : libraryEntity.getLibraryPaths()) {
            Path path = Path.of(pathEntity.getPath());
            if (!Files.exists(path) || !Files.isDirectory(path) || !Files.isReadable(path)) {
                log.error("Library path not accessible: {}", path);
                throw ApiError.LIBRARY_PATH_NOT_ACCESSIBLE.createException(path.toString());
            }
        }
    }

    protected static List<Long> detectDeletedBookIds(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Set<Path> currentFullPaths = libraryFiles.stream()
                .map(LibraryFile::getFullPath)
                .collect(Collectors.toSet());

        return libraryEntity.getBookEntities().stream()
                .filter(book -> (book.getDeleted() == null || !book.getDeleted()))
                .filter(book -> {
                    if (book.getBookFiles() == null || book.getBookFiles().isEmpty()) {
                        return true;
                    }
                    return !currentFullPaths.contains(book.getFullFilePath());
                })
                .map(BookEntity::getId)
                .collect(Collectors.toList());
    }

    protected List<LibraryFile> detectNewBookPaths(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Set<String> existingKeys = libraryEntity.getBookEntities().stream()
                .filter(book -> book.getBookFiles() != null && !book.getBookFiles().isEmpty())
                .map(this::generateUniqueKey)
                .collect(Collectors.toSet());

        Set<String> additionalFileKeys = bookAdditionalFileRepository.findByLibraryId(libraryEntity.getId()).stream()
                .map(this::generateUniqueKey)
                .collect(Collectors.toSet());

        existingKeys.addAll(additionalFileKeys);

        return libraryFiles.stream()
                .filter(file -> !existingKeys.contains(generateUniqueKey(file)))
                .collect(Collectors.toList());
    }

    private static final double FUZZY_MATCH_THRESHOLD = 0.85;

    private BookEntity findMatchingBook(LibraryFile file) {
        String fileSubPath = file.getFileSubPath();

        // Skip root-level files
        if (fileSubPath == null || fileSubPath.isEmpty()) {
            return null;
        }

        String fileGroupingKey = BookFileGroupingUtils.extractGroupingKey(file.getFileName());
        Long libraryPathId = file.getLibraryPathEntity().getId();

        List<BookEntity> booksInDirectory = bookRepository.findAllByLibraryPathIdAndFileSubPath(libraryPathId, fileSubPath);

        BookEntity fuzzyMatch = null;
        double bestSimilarity = 0;

        for (BookEntity book : booksInDirectory) {
            if (book.getDeleted() != null && book.getDeleted()) {
                continue;
            }
            BookFileEntity primaryFile = book.getPrimaryBookFile();
            String existingGroupingKey = BookFileGroupingUtils.extractGroupingKey(primaryFile.getFileName());

            // Try exact match first
            if (fileGroupingKey.equals(existingGroupingKey)) {
                return book;
            }

            // Track best fuzzy match
            double similarity = BookFileGroupingUtils.calculateSimilarity(fileGroupingKey, existingGroupingKey);
            if (similarity >= FUZZY_MATCH_THRESHOLD && similarity > bestSimilarity) {
                bestSimilarity = similarity;
                fuzzyMatch = book;
            }
        }

        // Return fuzzy match if found
        if (fuzzyMatch != null) {
            log.debug("Fuzzy matched '{}' to '{}' with similarity {}", file.getFileName(),
                    fuzzyMatch.getPrimaryBookFile().getFileName(), bestSimilarity);
        }
        return fuzzyMatch;
    }

    private void autoAttachFile(BookEntity book, LibraryFile file) {
        String hash = FileFingerprint.generateHash(file.getFullPath());
        BookFileEntity additionalFile = BookFileEntity.builder()
                .book(book)
                .fileName(file.getFileName())
                .fileSubPath(file.getFileSubPath())
                .isBookFormat(true)
                .bookType(file.getBookFileType())
                .fileSizeKb(FileUtils.getFileSizeInKb(file.getFullPath()))
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(Instant.now())
                .build();

        try {
            bookAdditionalFileRepository.save(additionalFile);
            log.info("Auto-attached new format {} to existing book: {}", file.getFileName(), book.getPrimaryBookFile().getFileName());
        } catch (Exception e) {
            log.error("Error auto-attaching file {}: {}", file.getFileName(), e.getMessage());
        }
    }

    private String generateUniqueKey(BookEntity book) {
        return generateKey(book.getLibraryPath().getId(), book.getPrimaryBookFile().getFileSubPath(), book.getPrimaryBookFile().getFileName());
    }

    private String generateUniqueKey(BookFileEntity file) {
        return generateKey(file.getBook().getLibraryPath().getId(), file.getFileSubPath(), file.getFileName());
    }

    private String generateUniqueKey(LibraryFile file) {
        return generateKey(file.getLibraryPathEntity().getId(), file.getFileSubPath(), file.getFileName());
    }

    private String generateKey(Long libraryPathId, String subPath, String fileName) {
        String safeSubPath = (subPath == null) ? "" : subPath;
        return libraryPathId + ":" + safeSubPath + ":" + fileName;
    }

    protected List<Long> detectDeletedAdditionalFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Set<String> currentFileKeys = libraryFiles.stream()
                .map(this::generateUniqueKey)
                .collect(Collectors.toSet());

        List<BookFileEntity> allAdditionalFiles = bookAdditionalFileRepository.findByLibraryId(libraryEntity.getId());

        return allAdditionalFiles.stream()
                .filter(BookFileEntity::isBookFormat)
                .filter(additionalFile -> !currentFileKeys.contains(generateUniqueKey(additionalFile)))
                .map(BookFileEntity::getId)
                .collect(Collectors.toList());
    }
}
