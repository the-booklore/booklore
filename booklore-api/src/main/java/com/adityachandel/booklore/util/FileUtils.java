package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

@UtilityClass
@Slf4j
public class FileUtils {

    private final String FILE_NOT_FOUND_MESSAGE = "File does not exist: ";

    public String getBookFullPath(BookEntity bookEntity) {
        BookFileEntity bookFile = bookEntity.getPrimaryBookFile();

        return Path.of(bookEntity.getLibraryPath().getPath(), bookFile.getFileSubPath(), bookFile.getFileName())
                .normalize()
                .toString()
                .replace("\\", "/");
    }

    public String getBookFullPath(Book book) {
        return Path.of(book.getLibraryPath().getPath(), book.getFileSubPath(), book.getFileName())
                .normalize()
                .toString()
                .replace("\\", "/");
    }

    public String getRelativeSubPath(String basePath, Path fullFilePath) {
        return Optional.ofNullable(Path.of(basePath)
                        .relativize(fullFilePath)
                        .getParent())
                .map(path -> path.toString().replace("\\", "/"))
                .orElse("");
    }

    public Long getFileSizeInKb(BookEntity bookEntity) {
        Path filePath = Path.of(getBookFullPath(bookEntity));
        return getFileSizeInKb(filePath);
    }

    public Long getFileSizeInKb(Path filePath) {
        try {
            if (!Files.exists(filePath)) {
                log.warn(FILE_NOT_FOUND_MESSAGE + "{}", filePath.toAbsolutePath());
                return null;
            }
            return Files.size(filePath) / 1024;
        } catch (IOException e) {
            log.error("Failed to get file size for path [{}]: {}", filePath, e.getMessage(), e);
            return null;
        }
    }

    public void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;

        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    public String getExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int i = fileName.lastIndexOf('.');
        if (i >= 0 && i < fileName.length() - 1) {
            return fileName.substring(i + 1);
        }
        return "";
    }

    final private List<String> systemDirs = Arrays.asList(
      // synology
      "#recycle",
      "@eaDir",
      // calibre
      ".caltrash"
    );

    public boolean shouldIgnore(Path path) {
        if (!path.getFileName().toString().isEmpty() && path.getFileName().toString().charAt(0) == '.') {
            return true;
        }
        for (Path part : path) {
            if (systemDirs.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    public static Stream<Path> walk(Path start, FileVisitOption... options) throws IOException {
        return walk(start, Integer.MAX_VALUE, options);
    }

    // Drop in replacement for Files.walk that should handle io errors by ignoring them. Allows for unaccessible directories like trash inside of a library.
    public Stream<Path> walk(Path path, int maxDepth, FileVisitOption... options) throws IOException {
        final List<Path> files = new ArrayList<>();

        Files.walkFileTree(path, Set.of(options), maxDepth, new SimpleFileVisitor<>() {
            @Override
            @NonNull
            public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
                if (FileUtils.shouldIgnore(file) || !Files.isReadable(file)) {
                    return FileVisitResult.CONTINUE;
                }
                files.add(file);
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
                files.add(dir);
                return super.preVisitDirectory(dir, attrs);
            }
        });

        return files.stream();
    }
}
