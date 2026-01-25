package com.adityachandel.booklore.service.watcher;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.websocket.LogNotification;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.service.library.LibraryProcessingService;
import com.adityachandel.booklore.util.BookFileGroupingUtils;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.adityachandel.booklore.model.enums.PermissionType.ADMIN;
import static com.adityachandel.booklore.model.enums.PermissionType.MANAGE_LIBRARY;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookFileTransactionalHandler {

    private final BookFilePersistenceService bookFilePersistenceService;
    private final LibraryProcessingService libraryProcessingService;
    private final NotificationService notificationService;
    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;

    @Transactional()
    public void handleNewBookFile(long libraryId, Path path) {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        String filePath = path.toString();
        String fileName = path.getFileName().toString();
        String libraryPath = bookFilePersistenceService.findMatchingLibraryPath(libraryEntity, path);

        notificationService.sendMessageToPermissions(Topic.LOG, LogNotification.info("Started processing file: " + filePath), Set.of(ADMIN, MANAGE_LIBRARY));

        LibraryPathEntity libraryPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(libraryEntity, libraryPath);
        String fileSubPath = FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), path);

        BookEntity matchingBook = findMatchingBook(libraryPathEntity.getId(), fileSubPath, fileName);

        if (matchingBook != null) {
            autoAttachFile(matchingBook, fileName, fileSubPath, path);
            log.info("[CREATE] Auto-attached file '{}' to existing book", filePath);
        } else {
            LibraryFile libraryFile = LibraryFile.builder()
                    .libraryEntity(libraryEntity)
                    .libraryPathEntity(libraryPathEntity)
                    .fileSubPath(fileSubPath)
                    .fileName(fileName)
                    .bookFileType(BookFileExtension.fromFileName(fileName)
                            .map(BookFileExtension::getType)
                            .orElseThrow(() -> new IllegalArgumentException("Unsupported book file type: " + fileName)))
                    .build();

            libraryProcessingService.processLibraryFiles(List.of(libraryFile), libraryEntity);
            log.info("[CREATE] Completed processing for file '{}'", filePath);
        }

        notificationService.sendMessageToPermissions(Topic.LOG, LogNotification.info("Finished processing file: " + filePath), Set.of(ADMIN, MANAGE_LIBRARY));
    }

    private static final double FUZZY_MATCH_THRESHOLD = 0.85;

    private BookEntity findMatchingBook(Long libraryPathId, String fileSubPath, String fileName) {
        // Skip root-level files
        if (fileSubPath == null || fileSubPath.isEmpty()) {
            return null;
        }

        String fileGroupingKey = BookFileGroupingUtils.extractGroupingKey(fileName);

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
            log.debug("Fuzzy matched '{}' to '{}' with similarity {}", fileName,
                    fuzzyMatch.getPrimaryBookFile().getFileName(), bestSimilarity);
        }
        return fuzzyMatch;
    }

    private void autoAttachFile(BookEntity book, String fileName, String fileSubPath, Path fullPath) {
        String hash = FileFingerprint.generateHash(fullPath);
        BookFileEntity additionalFile = BookFileEntity.builder()
                .book(book)
                .fileName(fileName)
                .fileSubPath(fileSubPath)
                .isBookFormat(true)
                .bookType(BookFileExtension.fromFileName(fileName)
                        .map(BookFileExtension::getType)
                        .orElse(null))
                .fileSizeKb(FileUtils.getFileSizeInKb(fullPath))
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(Instant.now())
                .build();

        bookAdditionalFileRepository.save(additionalFile);
        log.info("Auto-attached new format {} to existing book: {}", fileName, book.getPrimaryBookFile().getFileName());
    }
}
