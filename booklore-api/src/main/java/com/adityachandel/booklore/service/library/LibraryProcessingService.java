package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.websocket.LogNotification;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.task.options.RescanLibraryContext;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class LibraryProcessingService {

    private final LibraryRepository libraryRepository;
    private final NotificationService notificationService;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final LibraryFileProcessorRegistry fileProcessorRegistry;
    private final BookRestorationService bookRestorationService;
    private final BookDeletionService bookDeletionService;
    private final LibraryFileHelper libraryFileHelper;
    @PersistenceContext
    private final EntityManager entityManager;

    @Transactional
    public void processLibrary(long libraryId) {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        notificationService.sendMessage(Topic.LOG, LogNotification.info("Started processing library: " + libraryEntity.getName()));
        LibraryFileProcessor processor = fileProcessorRegistry.getProcessor(libraryEntity);
        try {
            List<LibraryFile> libraryFiles = libraryFileHelper.getLibraryFiles(libraryEntity, processor);
            processor.processLibraryFiles(detectNewBookPaths(libraryFiles, libraryEntity), libraryEntity);
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
        LibraryFileProcessor processor = fileProcessorRegistry.getProcessor(libraryEntity);
        
        validateLibraryPathsAccessible(libraryEntity);
        
        List<LibraryFile> libraryFiles = libraryFileHelper.getLibraryFiles(libraryEntity, processor);
        
        int existingBookCount = libraryEntity.getBookEntities().size();
        if (existingBookCount > 0 && libraryFiles.isEmpty()) {
            String paths = libraryEntity.getLibraryPaths().stream()
                    .map(p -> p.getPath())
                    .collect(Collectors.joining(", "));
            log.error("Library '{}' has {} existing books but scan found 0 files. Paths may be offline: {}", 
                    libraryEntity.getName(), existingBookCount, paths);
            throw ApiError.LIBRARY_PATH_NOT_ACCESSIBLE.createException(paths);
        }
        
        List<Long> additionalFileIds = detectDeletedAdditionalFiles(libraryFiles, libraryEntity, processor);
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
        processor.processLibraryFiles(detectNewBookPaths(libraryFiles, libraryEntity), libraryEntity);

        notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished refreshing library: " + libraryEntity.getName()));
    }

    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        LibraryFileProcessor processor = fileProcessorRegistry.getProcessor(libraryEntity);
        processor.processLibraryFiles(libraryFiles, libraryEntity);
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

    private String generateUniqueKey(BookEntity book) {
        return generateKey(book.getLibraryPath().getId(), book.getPrimaryBookFile().getFileSubPath(), book.getPrimaryBookFile().getFileName());
    }

    private String generateUniqueKey(BookFileEntity file) {
        // Additional files inherit library path from their parent book
        return generateKey(file.getBook().getLibraryPath().getId(), file.getFileSubPath(), file.getFileName());
    }

    private String generateUniqueKey(LibraryFile file) {
        return generateKey(file.getLibraryPathEntity().getId(), file.getFileSubPath(), file.getFileName());
    }

    private String generateKey(Long libraryPathId, String subPath, String fileName) {
        String safeSubPath = (subPath == null) ? "" : subPath;
        return libraryPathId + ":" + safeSubPath + ":" + fileName;
    }

    protected List<Long> detectDeletedAdditionalFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity, LibraryFileProcessor processor) {
        Set<String> currentFileKeys = libraryFiles.stream()
                .map(this::generateUniqueKey)
                .collect(Collectors.toSet());

        List<BookFileEntity> allAdditionalFiles = bookAdditionalFileRepository.findByLibraryId(libraryEntity.getId());

        return allAdditionalFiles.stream()
                // Only check files that would be scanned: book formats always, non-book files only if processor supports them
                .filter(additionalFile -> additionalFile.isBookFormat() || processor.supportsSupplementaryFiles())
                .filter(additionalFile -> !currentFileKeys.contains(generateUniqueKey(additionalFile)))
                .map(BookFileEntity::getId)
                .collect(Collectors.toList());
    }
}
