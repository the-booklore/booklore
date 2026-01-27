package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.LibraryOrganizationMode;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.util.BookFileGroupingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Unified service for grouping book files.
 * Handles both initial scan (grouping new files) and rescan (matching to existing books).
 * Organization mode determines the grouping strategy.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookGroupingService {

    private static final double FILELESS_MATCH_THRESHOLD = 0.85;

    private final BookRepository bookRepository;

    /**
     * Result of grouping operation for rescan.
     * Contains files to attach to existing books and files that need new book entities.
     */
    public record GroupingResult(
            Map<BookEntity, List<LibraryFile>> filesToAttach,
            Map<String, List<LibraryFile>> newBookGroups
    ) {}

    /**
     * Groups new files for initial library scan.
     * Returns groups of files that should become a single book entity.
     *
     * @param newFiles        files to group
     * @param libraryEntity   the library being processed
     * @return map of group key to list of files in that group
     */
    public Map<String, List<LibraryFile>> groupForInitialScan(List<LibraryFile> newFiles, LibraryEntity libraryEntity) {
        LibraryOrganizationMode mode = getOrganizationMode(libraryEntity);

        return switch (mode) {
            case BOOK_PER_FOLDER -> groupByFolder(newFiles);
            case AUTO_DETECT -> BookFileGroupingUtils.groupByBaseName(newFiles);
        };
    }

    /**
     * Groups new files for rescan, considering existing books.
     * Returns files to attach to existing books and new groups to create.
     *
     * @param newFiles        files to process
     * @param libraryEntity   the library being processed
     * @return grouping result with attach map and new groups
     */
    public GroupingResult groupForRescan(List<LibraryFile> newFiles, LibraryEntity libraryEntity) {
        LibraryOrganizationMode mode = getOrganizationMode(libraryEntity);

        Map<BookEntity, List<LibraryFile>> filesToAttach = new LinkedHashMap<>();
        List<LibraryFile> unmatched = new ArrayList<>();

        for (LibraryFile file : newFiles) {
            BookEntity match = findMatchingBook(file, mode);
            if (match != null) {
                filesToAttach.computeIfAbsent(match, k -> new ArrayList<>()).add(file);
            } else {
                unmatched.add(file);
            }
        }

        // Group unmatched files into new book groups
        Map<String, List<LibraryFile>> newBookGroups;
        if (unmatched.isEmpty()) {
            newBookGroups = Collections.emptyMap();
        } else {
            newBookGroups = switch (mode) {
                case BOOK_PER_FOLDER -> groupByFolder(unmatched);
                case AUTO_DETECT -> BookFileGroupingUtils.groupByBaseName(unmatched);
            };
        }

        return new GroupingResult(filesToAttach, newBookGroups);
    }

    /**
     * Simple folder-based grouping: all files in the same folder become one book.
     */
    private Map<String, List<LibraryFile>> groupByFolder(List<LibraryFile> files) {
        Map<String, List<LibraryFile>> result = new LinkedHashMap<>();

        for (LibraryFile file : files) {
            String folderKey = file.getLibraryPathEntity().getId() + ":" +
                    (file.getFileSubPath() == null ? "" : file.getFileSubPath());
            result.computeIfAbsent(folderKey, k -> new ArrayList<>()).add(file);
        }

        log.debug("BOOK_PER_FOLDER grouping: {} files into {} groups", files.size(), result.size());
        return result;
    }

    /**
     * Finds an existing book that matches the given file.
     * Strategy depends on organization mode.
     */
    private BookEntity findMatchingBook(LibraryFile file, LibraryOrganizationMode mode) {
        // First check for fileless books by metadata matching
        BookEntity filelessMatch = findMatchingFilelessBook(file, file.getLibraryEntity());
        if (filelessMatch != null) {
            return filelessMatch;
        }

        String fileSubPath = file.getFileSubPath();

        // Root-level files: don't auto-attach
        if (fileSubPath == null || fileSubPath.isEmpty()) {
            return null;
        }

        Long libraryPathId = file.getLibraryPathEntity().getId();
        List<BookEntity> booksInDirectory = bookRepository.findAllByLibraryPathIdAndFileSubPath(libraryPathId, fileSubPath);

        // Filter to non-deleted books only
        List<BookEntity> activeBooksInDirectory = booksInDirectory.stream()
                .filter(book -> book.getDeleted() == null || !book.getDeleted())
                .toList();

        if (activeBooksInDirectory.isEmpty()) {
            return null;
        }

        return switch (mode) {
            case BOOK_PER_FOLDER -> findMatchBookPerFolder(file, activeBooksInDirectory);
            case AUTO_DETECT -> findMatchAutoDetect(file, activeBooksInDirectory);
        };
    }

    /**
     * Finds a fileless book that matches the given file by metadata title.
     * Only matches books that either have no libraryPath or have the same libraryPath as the file.
     */
    private BookEntity findMatchingFilelessBook(LibraryFile file, LibraryEntity library) {
        List<BookEntity> filelessBooks = bookRepository.findFilelessBooksByLibraryId(library.getId());
        if (filelessBooks.isEmpty()) {
            return null;
        }

        String fileBaseName = BookFileGroupingUtils.extractGroupingKey(file.getFileName());
        Long fileLibraryPathId = file.getLibraryPathEntity().getId();

        for (BookEntity book : filelessBooks) {
            // Skip books that already have a different libraryPath
            if (book.getLibraryPath() != null && !book.getLibraryPath().getId().equals(fileLibraryPathId)) {
                continue;
            }

            if (book.getMetadata() != null && book.getMetadata().getTitle() != null) {
                String bookTitle = BookFileGroupingUtils.extractGroupingKey(book.getMetadata().getTitle());
                double similarity = BookFileGroupingUtils.calculateSimilarity(fileBaseName, bookTitle);
                if (similarity >= FILELESS_MATCH_THRESHOLD) {
                    log.debug("Matched file '{}' to fileless book '{}' (title: {})",
                            file.getFileName(), book.getId(), book.getMetadata().getTitle());
                    return book;
                }
            }
        }
        return null;
    }

    /**
     * BOOK_PER_FOLDER: If exactly one book in folder, attach to it.
     * If multiple books exist (edge case), use filename matching.
     */
    private BookEntity findMatchBookPerFolder(LibraryFile file, List<BookEntity> booksInDirectory) {
        // Filter to books with files for filename-based matching
        List<BookEntity> booksWithFiles = booksInDirectory.stream()
                .filter(BookEntity::hasFiles)
                .toList();

        if (booksWithFiles.size() == 1) {
            BookEntity book = booksWithFiles.get(0);
            log.debug("BOOK_PER_FOLDER: Attaching '{}' to single book in folder: '{}'",
                    file.getFileName(), book.getPrimaryBookFile().getFileName());
            return book;
        }

        if (booksWithFiles.isEmpty()) {
            return null;
        }

        // Multiple books in folder - shouldn't happen with BOOK_PER_FOLDER mode
        // Fall back to exact filename matching
        log.warn("BOOK_PER_FOLDER: Multiple books ({}) in folder '{}', using filename match",
                booksWithFiles.size(), file.getFileSubPath());
        return findExactMatch(file, booksWithFiles);
    }

    /**
     * AUTO_DETECT: Use fuzzy matching to find best match.
     */
    private BookEntity findMatchAutoDetect(LibraryFile file, List<BookEntity> booksInDirectory) {
        // Filter to books with files for filename-based matching
        List<BookEntity> booksWithFiles = booksInDirectory.stream()
                .filter(BookEntity::hasFiles)
                .toList();

        if (booksWithFiles.isEmpty()) {
            return null;
        }

        // If exactly one book with files in folder, attach to it
        if (booksWithFiles.size() == 1) {
            BookEntity book = booksWithFiles.get(0);
            log.debug("AUTO_DETECT: Single book in folder '{}', attaching '{}' to '{}'",
                    file.getFileSubPath(), file.getFileName(), book.getPrimaryBookFile().getFileName());
            return book;
        }

        // Multiple books: use fuzzy matching
        String fileGroupingKey = BookFileGroupingUtils.extractGroupingKey(file.getFileName());
        BookEntity bestMatch = null;
        double bestSimilarity = 0;

        for (BookEntity book : booksWithFiles) {
            BookFileEntity primaryFile = book.getPrimaryBookFile();
            String existingGroupingKey = BookFileGroupingUtils.extractGroupingKey(primaryFile.getFileName());

            // Exact match
            if (fileGroupingKey.equals(existingGroupingKey)) {
                return book;
            }

            // Track best fuzzy match
            double similarity = BookFileGroupingUtils.calculateSimilarity(fileGroupingKey, existingGroupingKey);
            if (similarity >= 0.85 && similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = book;
            }
        }

        if (bestMatch != null) {
            log.debug("AUTO_DETECT: Fuzzy matched '{}' to '{}' (similarity: {})",
                    file.getFileName(), bestMatch.getPrimaryBookFile().getFileName(), bestSimilarity);
        }
        return bestMatch;
    }

    /**
     * Finds a book with exact filename match.
     * Assumes books list contains only books with files.
     */
    private BookEntity findExactMatch(LibraryFile file, List<BookEntity> books) {
        String fileKey = BookFileGroupingUtils.extractGroupingKey(file.getFileName());

        for (BookEntity book : books) {
            BookFileEntity primaryFile = book.getPrimaryBookFile();
            if (primaryFile == null) {
                continue;
            }
            String bookKey = BookFileGroupingUtils.extractGroupingKey(primaryFile.getFileName());
            if (fileKey.equals(bookKey)) {
                return book;
            }
        }
        return null;
    }

    private LibraryOrganizationMode getOrganizationMode(LibraryEntity library) {
        LibraryOrganizationMode mode = library.getOrganizationMode();
        return mode != null ? mode : LibraryOrganizationMode.AUTO_DETECT;
    }
}
