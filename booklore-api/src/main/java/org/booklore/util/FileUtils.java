package org.booklore.util;

import org.booklore.model.dto.Book;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@UtilityClass
@Slf4j
public class FileUtils {

    private final String FILE_NOT_FOUND_MESSAGE = "File does not exist: ";

    public String getBookFullPath(BookEntity bookEntity) {
        BookFileEntity bookFile = bookEntity.getPrimaryBookFile();
        if (bookFile == null || bookEntity.getLibraryPath() == null) {
            return null;
        }

        return Path.of(bookEntity.getLibraryPath().getPath(), bookFile.getFileSubPath(), bookFile.getFileName())
                .normalize()
                .toString()
                .replace("\\", "/");
    }

    public String getBookFullPath(BookEntity bookEntity, BookFileEntity bookFile) {
        if (bookFile == null || bookEntity.getLibraryPath() == null) {
            return null;
        }

        return Path.of(bookEntity.getLibraryPath().getPath(), bookFile.getFileSubPath(), bookFile.getFileName())
                .normalize()
                .toString()
                .replace("\\", "/");
    }

    public String getBookFullPath(Book book) {
        return book.getPrimaryFile().getFilePath();
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

    /**
     * Calculate total size of all files in a folder (for folder-based audiobooks).
     */
    public Long getFolderSizeInKb(Path folderPath) {
        try {
            if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
                log.warn("Folder does not exist or is not a directory: {}", folderPath.toAbsolutePath());
                return null;
            }
            long totalBytes = Files.walk(folderPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
            return totalBytes / 1024;
        } catch (IOException e) {
            log.error("Failed to get folder size for path [{}]: {}", folderPath, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get the first audio file in a folder (sorted alphabetically).
     */
    public Optional<Path> getFirstAudioFileInFolder(Path folderPath) {
        try {
            if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
                return Optional.empty();
            }
            return Files.list(folderPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> isAudioFile(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .findFirst();
        } catch (IOException e) {
            log.error("Failed to list folder [{}]: {}", folderPath, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Check if a filename is an audio file.
     */
    public boolean isAudioFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".m4b");
    }

    /**
     * List all audio files in a folder, sorted alphabetically.
     */
    public List<Path> listAudioFilesInFolder(Path folderPath) {
        try {
            if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
                return List.of();
            }
            return Files.list(folderPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> isAudioFile(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list audio files in folder [{}]: {}", folderPath, e.getMessage(), e);
            return List.of();
        }
    }

    private static final Pattern LEADING_NUMBER_PREFIX = Pattern.compile("^\\d{1,3}(?:\\.|\\s*-)\\s*");
    private static final Pattern PART_DISC_INDICATOR = Pattern.compile(
            "\\s*[\\(\\[\\-]?\\s*(?:part|pt|dis[ck]|cd)\\s*\\d+\\s*[\\)\\]]?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRAILING_NUMBERS = Pattern.compile("\\s*\\d+\\s*$");
    private static final Set<String> GENERIC_AUDIO_TITLES = Set.of(
            "chapter", "track", "part", "disc", "disk", "cd", "side", "intro", "epilogue", "prologue", "outro"
    );

    /**
     * Determines if a list of audio files represents a series folder (each file is a separate book)
     * rather than a multi-file audiobook (chapter files for one book).
     *
     * Extracts a "base title" from each file by stripping numbering, part indicators, and extensions,
     * then counts distinct non-generic titles. If more than one distinct title exists, it's a series folder.
     */
    public boolean isSeriesFolder(List<Path> audioFiles) {
        Set<String> distinctTitles = new HashSet<>();
        for (Path file : audioFiles) {
            String title = extractBaseTitle(file.getFileName().toString());
            if (!title.isEmpty() && !GENERIC_AUDIO_TITLES.contains(title)) {
                distinctTitles.add(title);
            }
        }
        return distinctTitles.size() > 1;
    }

    private String extractBaseTitle(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        String baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
        baseName = LEADING_NUMBER_PREFIX.matcher(baseName).replaceFirst("");
        baseName = PART_DISC_INDICATOR.matcher(baseName).replaceAll("");
        baseName = TRAILING_NUMBERS.matcher(baseName).replaceAll("");
        return baseName.toLowerCase().trim();
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
}
