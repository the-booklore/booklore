package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.PathPatternResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@AllArgsConstructor
public class FileMovingHelper {

    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final AppSettingService appSettingService;

    /**
     * Generates the new file path based on the library's file naming pattern
     */
    public Path generateNewFilePath(BookEntity book, String pattern) {
        String newRelativePathStr = PathPatternResolver.resolvePattern(book, pattern);
        if (newRelativePathStr.startsWith("/") || newRelativePathStr.startsWith("\\")) {
            newRelativePathStr = newRelativePathStr.substring(1);
        }

        Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
        return libraryRoot.resolve(newRelativePathStr).normalize();
    }

    /**
     * Generates the new file path based on metadata and file naming pattern
     */
    public Path generateNewFilePath(String libraryRootPath, BookMetadata metadata, String pattern, String fileName) {
        String relativePath = PathPatternResolver.resolvePattern(metadata, pattern, FilenameUtils.getName(fileName));
        return Paths.get(libraryRootPath, relativePath);
    }

    /**
     * Gets the file naming pattern for a library, falling back to default if not set,
     * and finally to {currentFilename} if no patterns are available
     */
    public String getFileNamingPattern(LibraryEntity library) {
        String pattern = library.getFileNamingPattern();
        if (pattern == null || pattern.trim().isEmpty()) {
            try {
                pattern = appSettingService.getAppSettings().getUploadPattern();
                log.debug("Using default pattern for library {} as no custom pattern is set", library.getName());
            } catch (Exception e) {
                log.warn("Failed to get default upload pattern for library {}: {}", library.getName(), e.getMessage());
            }
        }
        if (pattern == null || pattern.trim().isEmpty()) {
            pattern = "{currentFilename}";
            log.info("No file naming pattern available for library {}. Using fallback pattern: {currentFilename}", library.getName());
        }

        // Ensure pattern ends with filename placeholder if it ends with separator
        if (pattern.endsWith("/") || pattern.endsWith("\\")) {
            pattern += "{currentFilename}";
        }

        return pattern;
    }

    /**
     * Checks if a book has all required path components for file operations
     */
    public boolean hasRequiredPathComponents(BookEntity book) {
        if (book.getLibraryPath() == null || book.getLibraryPath().getPath() == null ||
                book.getFileSubPath() == null || book.getFileName() == null) {
            log.error("Missing required path components for book id {}. Skipping.", book.getId());
            return false;
        }
        return true;
    }

    /**
     * Moves a book file if the current path differs from the expected pattern
     */
    public boolean moveBookFileIfNeeded(BookEntity book, String pattern) throws IOException {
        Path oldFilePath = book.getFullFilePath();
        Path newFilePath = generateNewFilePath(book, pattern);

        if (oldFilePath.equals(newFilePath)) {
            log.debug("Source and destination paths are identical for book id {}. Skipping.", book.getId());
            return false;
        }

        moveBookFileAndUpdatePaths(book, oldFilePath, newFilePath);
        return true;
    }

    /**
     * Moves a file from source to target path, creating directories as needed
     */
    public void moveFile(Path source, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        log.info("Moving file from {} to {}", source, target);
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Updates book entity paths after a file move
     */
    public void updateBookPaths(BookEntity book, Path newFilePath) {
        String newFileName = newFilePath.getFileName().toString();
        Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
        Path newRelativeSubPath = libraryRoot.relativize(newFilePath.getParent());
        String newFileSubPath = newRelativeSubPath.toString().replace('\\', '/');

        book.setFileSubPath(newFileSubPath);
        book.setFileName(newFileName);
    }

    /**
     * Moves additional files for a book based on the file naming pattern
     */
    public void moveAdditionalFiles(BookEntity book, String pattern) throws IOException {
        Map<String, Integer> fileNameCounter = new HashMap<>();

        for (BookAdditionalFileEntity additionalFile : book.getAdditionalFiles()) {
            moveAdditionalFile(book, additionalFile, pattern, fileNameCounter);
        }
    }

    /**
     * Deletes empty parent directories up to library roots
     */
    public void deleteEmptyParentDirsUpToLibraryFolders(Path currentDir, Set<Path> libraryRoots) throws IOException {
        Set<String> ignoredFilenames = Set.of(".DS_Store", "Thumbs.db");
        currentDir = currentDir.toAbsolutePath().normalize();

        Set<Path> normalizedRoots = new HashSet<>();
        for (Path root : libraryRoots) {
            normalizedRoots.add(root.toAbsolutePath().normalize());
        }

        while (currentDir != null) {
            if (isLibraryRoot(currentDir, normalizedRoots)) {
                log.debug("Reached library root: {}. Stopping cleanup.", currentDir);
                break;
            }

            File[] files = currentDir.toFile().listFiles();
            if (files == null) {
                log.warn("Cannot read directory: {}. Stopping cleanup.", currentDir);
                break;
            }

            if (hasOnlyIgnoredFiles(files, ignoredFilenames)) {
                deleteIgnoredFilesAndDirectory(files, currentDir);
                currentDir = currentDir.getParent();
            } else {
                log.debug("Directory {} contains important files. Stopping cleanup.", currentDir);
                break;
            }
        }
    }

    private void moveBookFileAndUpdatePaths(BookEntity book, Path oldFilePath, Path newFilePath) throws IOException {
        moveFile(oldFilePath, newFilePath);
        updateBookPaths(book, newFilePath);

        // Clean up empty directories
        try {
            Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
            deleteEmptyParentDirsUpToLibraryFolders(oldFilePath.getParent(), Set.of(libraryRoot));
        } catch (IOException e) {
            log.warn("Failed to clean up empty directories after moving book ID {}: {}", book.getId(), e.getMessage());
        }
    }

    private void moveAdditionalFile(BookEntity book, BookAdditionalFileEntity additionalFile, String pattern, Map<String, Integer> fileNameCounter) throws IOException {
        Path oldAdditionalFilePath = additionalFile.getFullFilePath();
        if (!Files.exists(oldAdditionalFilePath)) {
            log.warn("Additional file does not exist for book id {}: {}", book.getId(), oldAdditionalFilePath);
            return;
        }

        Path newAdditionalFilePath = generateAdditionalFilePath(book, additionalFile, pattern);
        newAdditionalFilePath = ensureUniqueFilePath(newAdditionalFilePath, fileNameCounter);

        if (oldAdditionalFilePath.equals(newAdditionalFilePath)) {
            log.debug("Source and destination paths are identical for additional file id {}. Skipping.", additionalFile.getId());
            return;
        }

        moveFile(oldAdditionalFilePath, newAdditionalFilePath);

        Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
        updateAdditionalFilePaths(additionalFile, newAdditionalFilePath, libraryRoot);
        bookAdditionalFileRepository.save(additionalFile);

        log.info("Updated additional file id {} with new path", additionalFile.getId());
    }

    private Path generateAdditionalFilePath(BookEntity book, BookAdditionalFileEntity additionalFile, String pattern) {
        String newRelativePathStr = PathPatternResolver.resolvePattern(book.getMetadata(), pattern, additionalFile.getFileName());
        // Fall back to the filename when resolver returns null/empty to avoid NPEs in callers/tests
        if (newRelativePathStr == null || newRelativePathStr.trim().isEmpty()) {
            newRelativePathStr = additionalFile.getFileName() != null ? additionalFile.getFileName() : "";
        }
        if (!newRelativePathStr.isEmpty() && (newRelativePathStr.startsWith("/") || newRelativePathStr.startsWith("\\"))) {
            newRelativePathStr = newRelativePathStr.substring(1);
        }

        Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
        return libraryRoot.resolve(newRelativePathStr).normalize();
    }

    private Path ensureUniqueFilePath(Path filePath, Map<String, Integer> fileNameCounter) {
        String fileName = filePath.getFileName().toString();
        String baseName = fileName;
        String extension = "";

        int lastDot = fileName.lastIndexOf(".");
        if (lastDot >= 0 && lastDot < fileName.length() - 1) {
            baseName = fileName.substring(0, lastDot);
            extension = fileName.substring(lastDot);
        }

        String fileKey = filePath.toString().toLowerCase();
        Integer count = fileNameCounter.get(fileKey);

        if (count == null) {
            fileNameCounter.put(fileKey, 1);
            return filePath;
        } else {
            count++;
            fileNameCounter.put(fileKey, count);
            String newFileName = baseName + "_" + count + extension;
            return filePath.getParent().resolve(newFileName);
        }
    }

    private void updateAdditionalFilePaths(BookAdditionalFileEntity additionalFile, Path newFilePath, Path libraryRoot) {
        String newFileName = newFilePath.getFileName().toString();
        Path newRelativeSubPath = libraryRoot.relativize(newFilePath.getParent());
        String newFileSubPath = newRelativeSubPath.toString().replace('\\', '/');

        additionalFile.setFileSubPath(newFileSubPath);
        additionalFile.setFileName(newFileName);
    }

    private boolean isLibraryRoot(Path currentDir, Set<Path> normalizedRoots) {
        for (Path root : normalizedRoots) {
            try {
                if (Files.isSameFile(root, currentDir)) {
                    return true;
                }
            } catch (IOException e) {
                log.warn("Failed to compare paths: {} and {}", root, currentDir);
            }
        }
        return false;
    }

    private boolean hasOnlyIgnoredFiles(File[] files, Set<String> ignoredFilenames) {
        for (File file : files) {
            if (!ignoredFilenames.contains(file.getName())) {
                return false;
            }
        }
        return true;
    }

    private void deleteIgnoredFilesAndDirectory(File[] files, Path currentDir) {
        for (File file : files) {
            try {
                Files.delete(file.toPath());
                log.info("Deleted ignored file: {}", file.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to delete ignored file: {}", file.getAbsolutePath());
            }
        }
        try {
            Files.delete(currentDir);
            log.info("Deleted empty directory: {}", currentDir);
        } catch (IOException e) {
            log.warn("Failed to delete directory: {}", currentDir, e);
        }
    }
}
