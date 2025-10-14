package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.util.FileUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Component
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

        try (Stream<Path> stream = Files.walk(libraryPath, FileVisitOption.FOLLOW_LINKS)) {
            return stream.filter(Files::isRegularFile)
                    .map(fullPath -> {
                        String fileName = fullPath.getFileName().toString();
                        Optional<BookFileExtension> bookExtension = BookFileExtension.fromFileName(fileName);

                        if (bookExtension.isEmpty() && !supportsSupplementaryFiles) {
                            return null;
                        }

                        return LibraryFile.builder()
                                .libraryEntity(libraryEntity)
                                .libraryPathEntity(pathEntity)
                                .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), fullPath))
                                .fileName(fileName)
                                .bookFileType(bookExtension.map(BookFileExtension::getType).orElse(null))
                                .build();
                    })
                    .filter(Objects::nonNull)
                    .filter(file -> !file.getFileName().startsWith("."))
                    .toList();
        }
    }
}
