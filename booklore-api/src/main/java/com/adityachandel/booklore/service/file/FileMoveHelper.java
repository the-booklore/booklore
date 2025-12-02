package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import com.adityachandel.booklore.util.PathPatternResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@AllArgsConstructor
public class FileMoveHelper {

    private final MonitoringRegistrationService monitoringRegistrationService;
    private final AppSettingService appSettingService;

    public void moveFile(Path source, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        log.info("Moving file from {} to {}", source, target);
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public Path moveFileWithBackup(Path source, Path target) throws IOException {
        Path tempPath = source.resolveSibling(source.getFileName().toString() + ".tmp_move");
        log.info("Moving file from {} to temporary location {}", source, tempPath);
        Files.move(source, tempPath, StandardCopyOption.REPLACE_EXISTING);
        return tempPath;
    }

    public void commitMove(Path tempPath, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        log.info("Committing move from temporary location {} to {}", tempPath, target);
        Files.move(tempPath, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void rollbackMove(Path tempPath, Path originalSource) {
        if (Files.exists(tempPath)) {
            try {
                log.info("Rolling back move from {} to {}", tempPath, originalSource);
                Files.move(tempPath, originalSource, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.error("Failed to rollback file move from {} to {}", tempPath, originalSource, e);
            }
        }
    }

    public String extractSubPath(Path filePath, LibraryPathEntity libraryPathEntity) {
        Path libraryRoot = Paths.get(libraryPathEntity.getPath()).toAbsolutePath().normalize();
        Path parentDir = filePath.getParent().toAbsolutePath().normalize();
        Path relativeSubPath = libraryRoot.relativize(parentDir);
        return relativeSubPath.toString().replace('\\', '/');
    }

    public void unregisterLibrary(Long libraryId) {
        monitoringRegistrationService.unregisterLibrary(libraryId);
    }

    public void registerLibraryPaths(Long libraryId, Path libraryRoot) {
        log.debug("Registering library paths for library {} with root {}", libraryId, libraryRoot);
        monitoringRegistrationService.registerLibraryPaths(libraryId, libraryRoot);
    }

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
        if (pattern.endsWith("/") || pattern.endsWith("\\")) {
            pattern += "{currentFilename}";
        }
        return pattern;
    }

    public Path generateNewFilePath(BookEntity book, LibraryPathEntity libraryPathEntity, String pattern) {
        String newRelativePathStr = PathPatternResolver.resolvePattern(book, pattern);
        if (newRelativePathStr.startsWith("/") || newRelativePathStr.startsWith("\\")) {
            newRelativePathStr = newRelativePathStr.substring(1);
        }
        String path = libraryPathEntity.getPath();
        return Paths.get(path, newRelativePathStr);
    }

    public void deleteEmptyParentDirsUpToLibraryFolders(Path currentDir, Set<Path> libraryRoots) {
        Path dir = currentDir;
        Set<String> ignoredFilenames = Set.of(".DS_Store", "Thumbs.db");
        dir = dir.toAbsolutePath().normalize();
        Set<Path> normalizedRoots = new HashSet<>();
        for (Path root : libraryRoots) {
            normalizedRoots.add(root.toAbsolutePath().normalize());
        }
        while (dir != null) {
            if (isLibraryRoot(dir, normalizedRoots)) {
                break;
            }
            File[] files = dir.toFile().listFiles();
            if (files == null) {
                log.warn("Cannot read directory: {}. Stopping cleanup.", dir);
                break;
            }
            if (hasOnlyIgnoredFiles(files, ignoredFilenames)) {
                deleteIgnoredFilesAndDirectory(files, dir);
                dir = dir.getParent();
            } else {
                break;
            }
        }
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
