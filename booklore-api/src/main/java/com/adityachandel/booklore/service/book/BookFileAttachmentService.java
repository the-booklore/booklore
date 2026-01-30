package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.entity.UserBookFileProgressEntity;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.UserBookProgressRepository;
import com.adityachandel.booklore.service.file.FileMoveHelper;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import com.adityachandel.booklore.service.progress.ReadingProgressService;
import com.adityachandel.booklore.util.PathPatternResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class BookFileAttachmentService {

    private final BookRepository bookRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final AuthenticationService authenticationService;
    private final ReadingProgressService readingProgressService;
    private final MonitoringRegistrationService monitoringRegistrationService;
    private final FileMoveHelper fileMoveHelper;
    private final BookMapper bookMapper;
    private final BookService bookService;

    @Transactional
    public Book attachBookFiles(Long targetBookId, List<Long> sourceBookIds, boolean deleteSourceBooks) {
        BookEntity targetBook = bookRepository.findByIdWithBookFiles(targetBookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(targetBookId));

        // Validate and deduplicate source book IDs
        Set<Long> uniqueSourceBookIds = new LinkedHashSet<>(sourceBookIds); // Preserves order, removes duplicates
        if (uniqueSourceBookIds.contains(targetBookId)) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Cannot attach a book to itself");
        }

        // Load all source books upfront to avoid Hibernate auto-flush issues when loading inside loop
        List<BookEntity> sourceBooks = new ArrayList<>();
        for (Long sourceBookId : uniqueSourceBookIds) {
            BookEntity sourceBook = bookRepository.findByIdWithBookFiles(sourceBookId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(sourceBookId));
            sourceBooks.add(sourceBook);
        }

        // === VALIDATION PHASE - Do all validation BEFORE any file operations ===

        // Validate target has a primary file
        BookFileEntity targetPrimaryFile = targetBook.getBookFiles().stream()
                .filter(BookFileEntity::isBookFormat)
                .findFirst()
                .orElseThrow(() -> ApiError.GENERIC_BAD_REQUEST.createException("Target book has no primary file"));

        // Validate all source books upfront
        for (BookEntity sourceBook : sourceBooks) {
            // Validate same library
            if (!targetBook.getLibrary().getId().equals(sourceBook.getLibrary().getId())) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Source book " + sourceBook.getId() + " must be in the same library as target");
            }

            // Validate source has exactly 1 book format file
            List<BookFileEntity> sourceBookFiles = sourceBook.getBookFiles().stream()
                    .filter(BookFileEntity::isBookFormat)
                    .collect(Collectors.toList());

            if (sourceBookFiles.isEmpty()) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Source book " + sourceBook.getId() + " has no book format files to attach");
            }

            if (sourceBookFiles.size() > 1) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Source book " + sourceBook.getId() + " has multiple book format files. Only single-file books can be attached.");
            }

            BookFileEntity fileToMove = sourceBookFiles.get(0);

            // Validate not folder-based
            if (fileToMove.isFolderBased()) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Source book " + sourceBook.getId() + " is a folder-based audiobook. Folder-based books cannot be attached.");
            }

            // Validate source file exists
            Path sourceFilePath = fileToMove.getFullFilePath();
            if (!Files.exists(sourceFilePath)) {
                throw ApiError.GENERIC_BAD_REQUEST.createException(
                        "Source file not found at expected location: " + sourceFilePath +
                        ". The file may have been moved, deleted, or the database record is out of sync.");
            }
        }

        // === SETUP PHASE - Calculate paths and naming ===

        // Get file naming pattern and determine if primary file is at pattern location
        String fileNamingPattern = fileMoveHelper.getFileNamingPattern(targetBook.getLibrary());
        String patternResolvedPath = PathPatternResolver.resolvePattern(targetBook, targetPrimaryFile, fileNamingPattern);
        Path libraryRootPath = Paths.get(targetBook.getLibraryPath().getPath());
        Path patternFullPath = libraryRootPath.resolve(patternResolvedPath);

        // Check if primary file is at the pattern location
        Path actualPrimaryFilePath = targetPrimaryFile.getFullFilePath();
        boolean primaryFileAtPatternLocation = Files.exists(patternFullPath) &&
                patternFullPath.normalize().equals(actualPrimaryFilePath.normalize());

        // Determine target directory - use pattern directory, fallback to library root if pattern has no directory
        Path targetDirectory = patternFullPath.getParent();
        if (targetDirectory == null) {
            // Pattern resolved to just a filename with no directory structure
            targetDirectory = libraryRootPath;
        }
        String targetFileSubPath = libraryRootPath.equals(targetDirectory)
                ? ""
                : libraryRootPath.relativize(targetDirectory).toString();

        // Use pattern-resolved filename (without extension) as base
        String patternFileName = Paths.get(patternResolvedPath).getFileName().toString();
        int lastDot = patternFileName.lastIndexOf('.');
        String baseFileName = lastDot > 0 ? patternFileName.substring(0, lastDot) : patternFileName;

        // Validate target primary file exists (if not at pattern location)
        if (!primaryFileAtPatternLocation && !Files.exists(actualPrimaryFilePath)) {
            throw ApiError.GENERIC_BAD_REQUEST.createException(
                    "Target book's primary file not found at expected location: " + actualPrimaryFilePath +
                    ". Please ensure the target book's files exist before attaching files.");
        }

        // === FILE OPERATION PHASE - All file operations inside try-finally for monitoring cleanup ===

        Long libraryId = targetBook.getLibrary().getId();
        List<BookEntity> sourceBooksToDelete = new ArrayList<>();
        List<Path> sourceDirectoriesToCleanup = new ArrayList<>();
        Set<Path> pathsToReregister = new HashSet<>();

        try {
            // Unregister target directory from monitoring
            try {
                monitoringRegistrationService.unregisterSpecificPath(targetDirectory);
            } catch (Exception ex) {
                log.warn("Failed to unregister target directory from monitoring: {}", targetDirectory, ex);
            }
            pathsToReregister.add(targetDirectory);

            // If primary file is NOT at pattern location, move all existing target book files there first
            if (!primaryFileAtPatternLocation) {
                log.info("Primary file not at pattern location, organizing target book files first");

                // Unregister source directories for existing files
                for (BookFileEntity existingFile : targetBook.getBookFiles()) {
                    Path currentPath = existingFile.getFullFilePath();
                    if (Files.exists(currentPath)) {
                        Path sourceDir = currentPath.getParent();
                        if (sourceDir != null) {
                            try {
                                monitoringRegistrationService.unregisterSpecificPath(sourceDir);
                            } catch (Exception ex) {
                                log.warn("Failed to unregister source directory from monitoring: {}", sourceDir, ex);
                            }
                            pathsToReregister.add(sourceDir);
                            sourceDirectoriesToCleanup.add(sourceDir);
                        }
                    }
                }

                // Create target directory if needed
                try {
                    Files.createDirectories(targetDirectory);
                } catch (IOException e) {
                    throw ApiError.INTERNAL_SERVER_ERROR.createException("Failed to create target directory: " + e.getMessage());
                }

                // Move all existing target book files to pattern location
                for (BookFileEntity existingFile : targetBook.getBookFiles()) {
                    Path currentPath = existingFile.getFullFilePath();
                    if (!Files.exists(currentPath)) {
                        log.warn("Skipping missing file during organization: {}", currentPath);
                        continue;
                    }

                    // Generate pattern-resolved filename for this file
                    String resolvedPath = PathPatternResolver.resolvePattern(targetBook, existingFile, fileNamingPattern);
                    String newFileName = Paths.get(resolvedPath).getFileName().toString();

                    // Resolve conflicts if file already exists at destination
                    newFileName = resolveFilenameConflict(targetDirectory, newFileName);
                    Path destinationPath = targetDirectory.resolve(newFileName);

                    if (!currentPath.normalize().equals(destinationPath.normalize())) {
                        try {
                            Files.move(currentPath, destinationPath);
                            log.info("Organized file from {} to {}", currentPath, destinationPath);

                            // Update database record
                            existingFile.setFileSubPath(targetFileSubPath);
                            existingFile.setFileName(newFileName);
                        } catch (IOException e) {
                            throw ApiError.INTERNAL_SERVER_ERROR.createException(
                                    "Failed to organize file " + currentPath + ": " + e.getMessage());
                        }
                    }
                }
            }

            // Track extension counts for handling same-extension conflicts
            // Initialize with existing extensions in target book
            Map<String, Integer> extensionCounts = new HashMap<>();
            for (BookFileEntity existingFile : targetBook.getBookFiles()) {
                if (existingFile.isBookFormat()) {
                    String ext = getFileExtension(existingFile.getFileName()).toLowerCase();
                    extensionCounts.merge(ext, 1, Integer::sum);
                }
            }

            // Process each source book
            for (BookEntity sourceBook : sourceBooks) {
                BookFileEntity fileToMove = sourceBook.getBookFiles().stream()
                        .filter(BookFileEntity::isBookFormat)
                        .findFirst()
                        .orElseThrow(); // Already validated above

                Path sourceFilePath = fileToMove.getFullFilePath();
                Path sourceDirectory = sourceFilePath.getParent();

                // Unregister source directory from monitoring to prevent spurious DELETE events
                if (sourceDirectory != null) {
                    try {
                        monitoringRegistrationService.unregisterSpecificPath(sourceDirectory);
                    } catch (Exception ex) {
                        log.warn("Failed to unregister source directory from monitoring: {}", sourceDirectory, ex);
                    }
                    pathsToReregister.add(sourceDirectory);
                }

                // Generate new filename using the base name determined earlier (from pattern or actual primary file)
                String extension = getFileExtension(fileToMove.getFileName()).toLowerCase();

                // Handle same-extension conflicts by adding suffix (_1, _2, etc.)
                int existingCount = extensionCounts.getOrDefault(extension, 0);
                String newFileName;
                if (extension.isEmpty()) {
                    // File has no extension
                    newFileName = existingCount > 0
                            ? baseFileName + "_" + existingCount
                            : baseFileName;
                } else {
                    newFileName = existingCount > 0
                            ? baseFileName + "_" + existingCount + "." + extension
                            : baseFileName + "." + extension;
                }
                extensionCounts.merge(extension, 1, Integer::sum);

                // Final conflict check against filesystem (in case of external files)
                newFileName = resolveFilenameConflict(targetDirectory, newFileName);

                // Move the physical file
                Path destinationPath = targetDirectory.resolve(newFileName);
                try {
                    Files.move(sourceFilePath, destinationPath);
                    log.info("Moved file from {} to {}", sourceFilePath, destinationPath);
                } catch (IOException e) {
                    log.error("Failed to move file from {} to {}", sourceFilePath, destinationPath, e);
                    throw ApiError.INTERNAL_SERVER_ERROR.createException("Failed to move file: " + e.getMessage());
                }

                // Update file entity with new path info - properly manage bidirectional relationship
                fileToMove.setFileSubPath(targetFileSubPath);
                fileToMove.setFileName(newFileName);

                // Remove from source book's collection and add to target's collection
                sourceBook.getBookFiles().remove(fileToMove);
                fileToMove.setBook(targetBook);
                targetBook.getBookFiles().add(fileToMove);

                // Track source directory for cleanup
                if (sourceDirectory != null) {
                    sourceDirectoriesToCleanup.add(sourceDirectory);
                }

                // Track source book for deletion if requested
                if (deleteSourceBooks) {
                    long remainingBookFiles = sourceBook.getBookFiles().stream()
                            .filter(BookFileEntity::isBookFormat)
                            .count();

                    if (remainingBookFiles == 0) {
                        sourceBooksToDelete.add(sourceBook);
                    }
                }
            }

            // Delete source books after all files are moved
            if (!sourceBooksToDelete.isEmpty()) {
                bookRepository.deleteAll(sourceBooksToDelete);
            }

            // Cleanup empty source directories
            Set<Path> libraryRoots = targetBook.getLibrary().getLibraryPaths().stream()
                    .map(LibraryPathEntity::getPath)
                    .map(Paths::get)
                    .map(Path::normalize)
                    .collect(Collectors.toSet());

            for (Path sourceDir : sourceDirectoriesToCleanup) {
                bookService.deleteEmptyParentDirsUpToLibraryFolders(sourceDir, libraryRoots);
            }
        } finally {
            // Re-register paths for monitoring (only if they still exist)
            for (Path path : pathsToReregister) {
                if (Files.exists(path) && Files.isDirectory(path)) {
                    try {
                        monitoringRegistrationService.registerSpecificPath(path, libraryId);
                    } catch (Exception ex) {
                        log.warn("Failed to re-register path for monitoring: {}", path, ex);
                    }
                }
            }
        }

        // Return updated target book
        return getUpdatedBook(targetBookId);
    }

    private Book getUpdatedBook(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookEntity refreshedTarget = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        UserBookProgressEntity userProgress = userBookProgressRepository.findByUserIdAndBookId(user.getId(), bookId)
                .orElse(new UserBookProgressEntity());
        UserBookFileProgressEntity fileProgress = readingProgressService
                .fetchUserFileProgress(user.getId(), Set.of(bookId))
                .get(bookId);

        Book book = bookMapper.toBook(refreshedTarget);
        book.setShelves(bookService.filterShelvesByUserId(book.getShelves(), user.getId()));
        readingProgressService.enrichBookWithProgress(book, userProgress, fileProgress);

        return book;
    }

    private String resolveFilenameConflict(Path targetDirectory, String originalFileName) {
        Path targetPath = targetDirectory.resolve(originalFileName);
        if (!Files.exists(targetPath)) {
            return originalFileName;
        }

        // Extract base name and extension
        String baseName;
        String extension;
        int lastDotIndex = originalFileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            baseName = originalFileName.substring(0, lastDotIndex);
            extension = originalFileName.substring(lastDotIndex);
        } else {
            baseName = originalFileName;
            extension = "";
        }

        // Try with incrementing suffix
        int counter = 1;
        String newFileName;
        do {
            newFileName = baseName + "_" + counter + extension;
            targetPath = targetDirectory.resolve(newFileName);
            counter++;
        } while (Files.exists(targetPath) && counter < 1000);

        if (counter >= 1000) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Could not resolve filename conflict for: " + originalFileName);
        }

        return newFileName;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
}
