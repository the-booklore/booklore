package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
public class LibraryFileHelper {

    private static final int MIN_AUDIO_FILES_FOR_FOLDER_AUDIOBOOK = 2;

    public List<LibraryFile> getLibraryFiles(LibraryEntity libraryEntity) throws IOException {
        List<LibraryFile> allFiles = new ArrayList<>();
        for (LibraryPathEntity pathEntity : libraryEntity.getLibraryPaths()) {
            allFiles.addAll(findLibraryFiles(pathEntity, libraryEntity));
        }
        return allFiles;
    }

    private List<LibraryFile> findLibraryFiles(LibraryPathEntity pathEntity, LibraryEntity libraryEntity) throws IOException {
        Path libraryPath = Path.of(pathEntity.getPath());
        List<LibraryFile> libraryFiles = new ArrayList<>();

        // Track directories that contain audio files for folder-based audiobook detection
        Map<Path, List<Path>> dirAudioFiles = new HashMap<>();
        Map<Path, Boolean> dirHasNonAudioBooks = new HashMap<>();
        Set<Path> processedAsFolderAudiobook = new HashSet<>();

        Files.walkFileTree(libraryPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            @NonNull
            public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
                if (FileUtils.shouldIgnore(file) || !Files.isReadable(file) || !Files.isRegularFile(file)) {
                    return FileVisitResult.CONTINUE;
                }

                String fileName = file.getFileName().toString();
                Optional<BookFileExtension> bookExtension = BookFileExtension.fromFileName(fileName);

                if (bookExtension.isEmpty()) {
                    return FileVisitResult.CONTINUE;
                }

                Path parentDir = file.getParent();
                BookFileType fileType = bookExtension.get().getType();

                if (fileType == BookFileType.AUDIOBOOK) {
                    // Track audio files per directory for folder-based detection
                    dirAudioFiles.computeIfAbsent(parentDir, k -> new ArrayList<>()).add(file);
                } else {
                    // Track that this directory has non-audio book files
                    dirHasNonAudioBooks.put(parentDir, true);

                    // Add non-audio files immediately
                    libraryFiles.add(LibraryFile.builder()
                            .libraryEntity(libraryEntity)
                            .libraryPathEntity(pathEntity)
                            .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), file))
                            .fileName(fileName)
                            .bookFileType(fileType)
                            .build());
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            @NonNull
            public FileVisitResult postVisitDirectory(@NonNull Path dir, IOException exc) throws IOException {
                // Check if this directory should be treated as a folder-based audiobook
                List<Path> audioFiles = dirAudioFiles.get(dir);
                boolean hasNonAudioBooks = dirHasNonAudioBooks.getOrDefault(dir, false);

                if (audioFiles != null && audioFiles.size() >= MIN_AUDIO_FILES_FOR_FOLDER_AUDIOBOOK && !hasNonAudioBooks) {
                    // This is a folder-based audiobook: contains 2+ audio files and no ebook files
                    // Don't treat library root as audiobook folder
                    if (!dir.equals(libraryPath)) {
                        log.info("Detected folder-based audiobook: {} ({} audio files)", dir.getFileName(), audioFiles.size());

                        processedAsFolderAudiobook.add(dir);

                        // Add a single LibraryFile representing the folder
                        libraryFiles.add(LibraryFile.builder()
                                .libraryEntity(libraryEntity)
                                .libraryPathEntity(pathEntity)
                                .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), dir))
                                .fileName(dir.getFileName().toString())
                                .bookFileType(BookFileType.AUDIOBOOK)
                                .folderBased(true)
                                .build());
                    } else {
                        // Library root - add individual audio files
                        addIndividualAudioFiles(audioFiles, libraryEntity, pathEntity, libraryFiles);
                    }
                } else if (audioFiles != null) {
                    // Not a folder-based audiobook - add individual audio files
                    // But skip if parent was already processed as folder-based audiobook
                    boolean parentIsAudiobookFolder = false;
                    Path parent = dir.getParent();
                    while (parent != null && parent.startsWith(libraryPath)) {
                        if (processedAsFolderAudiobook.contains(parent)) {
                            parentIsAudiobookFolder = true;
                            break;
                        }
                        parent = parent.getParent();
                    }

                    if (!parentIsAudiobookFolder) {
                        addIndividualAudioFiles(audioFiles, libraryEntity, pathEntity, libraryFiles);
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            private void addIndividualAudioFiles(List<Path> audioFiles, LibraryEntity libraryEntity,
                    LibraryPathEntity pathEntity, List<LibraryFile> libraryFiles) {
                for (Path audioFile : audioFiles) {
                    String fileName = audioFile.getFileName().toString();
                    Optional<BookFileExtension> ext = BookFileExtension.fromFileName(fileName);

                    libraryFiles.add(LibraryFile.builder()
                            .libraryEntity(libraryEntity)
                            .libraryPathEntity(pathEntity)
                            .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), audioFile))
                            .fileName(fileName)
                            .bookFileType(ext.map(BookFileExtension::getType).orElse(BookFileType.AUDIOBOOK))
                            .build());
                }
            }

            @Override
            @NonNull
            public FileVisitResult visitFileFailed(@NonNull Path file, IOException e) {
                log.error("Failed read path [{}]: {}", file, e.getMessage(), e);
                return FileVisitResult.CONTINUE;
            }

            @Override
            @NonNull
            public FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs) throws IOException {
                if (FileUtils.shouldIgnore(dir) || !Files.isReadable(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return super.preVisitDirectory(dir, attrs);
            }
        });
        return libraryFiles;
    }
}
