package com.adityachandel.booklore.service.watcher;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.BookFileType;
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
import java.util.Optional;
import java.util.Set;

import static com.adityachandel.booklore.model.enums.PermissionType.ADMIN;
import static com.adityachandel.booklore.model.enums.PermissionType.MANAGE_LIBRARY;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookFileTransactionalHandler {

    private static final double FILELESS_MATCH_THRESHOLD = 0.85;

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

        String currentHash = FileFingerprint.generateHash(path);
        Optional<BookEntity> existingByHash = bookRepository.findByCurrentHash(currentHash);
        if (existingByHash.isPresent()) {
            bookFilePersistenceService.updatePathIfChanged(existingByHash.get(), libraryEntity, path, currentHash);
            log.info("[CREATE] File '{}' recognized as moved file, updated existing book's path", filePath);
            notificationService.sendMessageToPermissions(Topic.LOG, LogNotification.info("Finished processing file: " + filePath), Set.of(ADMIN, MANAGE_LIBRARY));
            return;
        }

        BookEntity filelessMatch = findMatchingFilelessBook(libraryEntity, fileName, libraryPathEntity);
        if (filelessMatch != null) {
            if (filelessMatch.getLibraryPath() == null) {
                filelessMatch.setLibraryPath(libraryPathEntity);
                bookRepository.save(filelessMatch);
            }
            autoAttachFile(filelessMatch, fileName, fileSubPath, path);
            log.info("[CREATE] Attached file '{}' to fileless book id={}", filePath, filelessMatch.getId());
            notificationService.sendMessageToPermissions(Topic.LOG, LogNotification.info("Finished processing file: " + filePath), Set.of(ADMIN, MANAGE_LIBRARY));
            return;
        }

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

    @Transactional
    public void handleNewFolderAudiobook(long libraryId, Path folderPath) {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        String folderName = folderPath.getFileName().toString();
        String libraryPath = bookFilePersistenceService.findMatchingLibraryPath(libraryEntity, folderPath);

        notificationService.sendMessageToPermissions(Topic.LOG,
                LogNotification.info("Started processing folder audiobook: " + folderPath), Set.of(ADMIN, MANAGE_LIBRARY));

        LibraryPathEntity libraryPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(libraryEntity, libraryPath);
        String fileSubPath = FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), folderPath);

        BookEntity matchingBook = findMatchingBookForFolderAudiobook(libraryPathEntity.getId(), fileSubPath, folderName);

        if (matchingBook != null) {
            autoAttachFolderAudiobook(matchingBook, folderName, fileSubPath, folderPath);
            String primaryFileName = matchingBook.hasFiles() ? matchingBook.getPrimaryBookFile().getFileName() : "book#" + matchingBook.getId();
            log.info("[CREATE] Auto-attached folder audiobook '{}' to existing book '{}'", folderName, primaryFileName);
        } else {
            LibraryFile libraryFile = LibraryFile.builder()
                    .libraryEntity(libraryEntity)
                    .libraryPathEntity(libraryPathEntity)
                    .fileSubPath(fileSubPath)
                    .fileName(folderName)
                    .bookFileType(BookFileType.AUDIOBOOK)
                    .folderBased(true)
                    .build();

            libraryProcessingService.processLibraryFiles(List.of(libraryFile), libraryEntity);
            log.info("[CREATE] Completed processing folder audiobook '{}'", folderPath);
        }

        notificationService.sendMessageToPermissions(Topic.LOG,
                LogNotification.info("Finished processing folder audiobook: " + folderPath), Set.of(ADMIN, MANAGE_LIBRARY));
    }

    private static final double FUZZY_MATCH_THRESHOLD = 0.85;

    private BookEntity findMatchingFilelessBook(LibraryEntity library, String fileName, LibraryPathEntity fileLibraryPath) {
        List<BookEntity> filelessBooks = bookRepository.findFilelessBooksByLibraryId(library.getId());
        String fileBaseName = BookFileGroupingUtils.extractGroupingKey(fileName);

        for (BookEntity book : filelessBooks) {
            if (book.getLibraryPath() != null && !book.getLibraryPath().getId().equals(fileLibraryPath.getId())) {
                continue;
            }

            if (book.getMetadata() != null && book.getMetadata().getTitle() != null) {
                String bookTitle = BookFileGroupingUtils.extractGroupingKey(book.getMetadata().getTitle());
                double similarity = BookFileGroupingUtils.calculateSimilarity(fileBaseName, bookTitle);
                if (similarity >= FILELESS_MATCH_THRESHOLD) {
                    return book;
                }
            }
        }
        return null;
    }

    private BookEntity findMatchingBook(Long libraryPathId, String fileSubPath, String fileName) {
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
            if (primaryFile == null) {
                continue;
            }
            String existingGroupingKey = BookFileGroupingUtils.extractGroupingKey(primaryFile.getFileName());

            if (fileGroupingKey.equals(existingGroupingKey)) {
                return book;
            }

            double similarity = BookFileGroupingUtils.calculateSimilarity(fileGroupingKey, existingGroupingKey);
            if (similarity >= FUZZY_MATCH_THRESHOLD && similarity > bestSimilarity) {
                bestSimilarity = similarity;
                fuzzyMatch = book;
            }
        }

        if (fuzzyMatch != null) {
            String primaryFileName = fuzzyMatch.hasFiles() ? fuzzyMatch.getPrimaryBookFile().getFileName() : "book#" + fuzzyMatch.getId();
            log.debug("Fuzzy matched '{}' to '{}' with similarity {}", fileName, primaryFileName, bestSimilarity);
        }
        return fuzzyMatch;
    }

    private BookEntity findMatchingBookForFolderAudiobook(Long libraryPathId, String fileSubPath, String folderName) {
        String parentPath = Optional.ofNullable(fileSubPath)
                .filter(p -> !p.isEmpty())
                .map(p -> {
                    int lastSep = p.lastIndexOf('/');
                    if (lastSep == -1) {
                        lastSep = p.lastIndexOf('\\');
                    }
                    return lastSep > 0 ? p.substring(0, lastSep) : "";
                })
                .orElse("");

        String folderGroupingKey = BookFileGroupingUtils.extractGroupingKey(folderName);

        List<BookEntity> booksInParent = bookRepository.findAllByLibraryPathIdAndFileSubPath(libraryPathId, parentPath);

        BookEntity fuzzyMatch = null;
        double bestSimilarity = 0;

        for (BookEntity book : booksInParent) {
            if (book.getDeleted() != null && book.getDeleted()) {
                continue;
            }
            BookFileEntity primaryFile = book.getPrimaryBookFile();
            if (primaryFile == null) {
                continue;
            }
            String existingGroupingKey = BookFileGroupingUtils.extractGroupingKey(primaryFile.getFileName());

            if (folderGroupingKey.equals(existingGroupingKey)) {
                return book;
            }

            double similarity = BookFileGroupingUtils.calculateSimilarity(folderGroupingKey, existingGroupingKey);
            if (similarity >= FUZZY_MATCH_THRESHOLD && similarity > bestSimilarity) {
                bestSimilarity = similarity;
                fuzzyMatch = book;
            }
        }

        if (fuzzyMatch != null) {
            String primaryFileName = fuzzyMatch.hasFiles() ? fuzzyMatch.getPrimaryBookFile().getFileName() : "book#" + fuzzyMatch.getId();
            log.debug("Fuzzy matched folder '{}' to '{}' with similarity {}", folderName, primaryFileName, bestSimilarity);
        }
        return fuzzyMatch;
    }

    private void autoAttachFolderAudiobook(BookEntity book, String folderName, String fileSubPath, Path folderPath) {
        String hash = FileFingerprint.generateFolderHash(folderPath);
        BookFileEntity additionalFile = BookFileEntity.builder()
                .book(book)
                .fileName(folderName)
                .fileSubPath(fileSubPath)
                .isBookFormat(true)
                .folderBased(true)
                .bookType(BookFileType.AUDIOBOOK)
                .fileSizeKb(FileUtils.getFolderSizeInKb(folderPath))
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(Instant.now())
                .build();

        bookAdditionalFileRepository.save(additionalFile);
        String primaryFileName = book.hasFiles() ? book.getPrimaryBookFile().getFileName() : "book#" + book.getId();
        log.info("Auto-attached folder audiobook {} to existing book: {}", folderName, primaryFileName);
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
        String primaryFileName = book.hasFiles() ? book.getPrimaryBookFile().getFileName() : "book#" + book.getId();
        log.info("Auto-attached new format {} to existing book: {}", fileName, primaryFileName);
    }
}
