package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
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
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class LibraryFileHelper {

    public List<LibraryFile> getLibraryFiles(LibraryEntity libraryEntity, LibraryFileProcessor processor) throws IOException {
        List<LibraryFile> allFiles = new ArrayList<>();
        for (LibraryPathEntity pathEntity : libraryEntity.getLibraryPaths()) {
            allFiles.addAll(findLibraryFiles(pathEntity, libraryEntity, processor));
        }
        return allFiles;
    }

    private List<LibraryFile> findLibraryFiles(LibraryPathEntity pathEntity, LibraryEntity libraryEntity, LibraryFileProcessor processor) throws IOException {
        Path libraryPath = Path.of(pathEntity.getPath());
        boolean supportsSupplementaryFiles = processor.supportsSupplementaryFiles();
        List<LibraryFile> libraryFiles = new ArrayList<>();

        Files.walkFileTree(libraryPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            @NonNull
            public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
                if (FileUtils.shouldIgnore(file) || !Files.isReadable(file) || !Files.isRegularFile(file)) {
                    return FileVisitResult.CONTINUE;
                }

                String fileName = file.getFileName().toString();
                Optional<BookFileExtension> bookExtension = BookFileExtension.fromFileName(fileName);

                if (bookExtension.isEmpty() && !supportsSupplementaryFiles) {
                    return FileVisitResult.CONTINUE;
                }

                libraryFiles.add(LibraryFile.builder()
                        .libraryEntity(libraryEntity)
                        .libraryPathEntity(pathEntity)
                        .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), file))
                        .fileName(fileName)
                        .bookFileType(bookExtension.map(BookFileExtension::getType).orElse(null))
                        .build());
                return FileVisitResult.CONTINUE;
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
