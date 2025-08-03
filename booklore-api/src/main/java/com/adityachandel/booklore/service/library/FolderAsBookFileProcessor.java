package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
@Slf4j
public class FolderAsBookFileProcessor implements LibraryFileProcessor {

    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final NotificationService notificationService;
    private final BookFileProcessorRegistry bookFileProcessorRegistry;

    @Override
    public LibraryScanMode getScanMode() {
        return LibraryScanMode.FOLDER_AS_BOOK;
    }

    @Override
    public boolean supportsSupplementaryFiles() {
        // This processor supports supplementary files, as it processes all files in the folder.
        return true;
    }

    @Override
    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        // Group files by their directory path
        Map<Path, List<LibraryFile>> filesByDirectory = libraryFiles.stream()
                .collect(Collectors.groupingBy(libraryFile -> libraryFile.getFullPath().getParent()));

        log.info("Processing {} directories with {} total files for library: {}",
                filesByDirectory.size(), libraryFiles.size(), libraryEntity.getName());

        // Process each directory
        var sortedDirectories = filesByDirectory.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (Map.Entry<Path, List<LibraryFile>> entry : sortedDirectories) {
            Path directoryPath = entry.getKey();
            List<LibraryFile> filesInDirectory = entry.getValue();

            log.debug("Processing directory: {} with {} files", directoryPath, filesInDirectory.size());
            processDirectory(directoryPath, filesInDirectory, libraryEntity);
        }
    }

    private void processDirectory(Path directoryPath, List<LibraryFile> filesInDirectory, LibraryEntity libraryEntity) {
        // Check if there's already a book entity in this directory
        Optional<BookEntity> existingBook = findExistingBookInDirectory(directoryPath, libraryEntity);

        if (existingBook.isPresent()) {
            log.debug("Found existing book in directory {}: {}", directoryPath, existingBook.get().getFileName());
            // Book already exists, process remaining files as additional files
            processAdditionalFilesForExistingBook(existingBook.get(), filesInDirectory);
        } else {
            // No book exists in this directory, check parent directories
            Optional<BookEntity> parentBook = findBookInParentDirectory(directoryPath, libraryEntity);

            if (parentBook.isPresent()) {
                log.debug("Found parent book for directory {}: {}", directoryPath, parentBook.get().getFileName());
                // Parent directory has a book, treat all files as supplementary
                processSupplementaryFilesForParentBook(parentBook.get(), filesInDirectory);
            } else {
                log.debug("No existing book found, creating new book from directory: {}", directoryPath);
                // No parent book, create a new book from this directory
                createNewBookFromDirectory(directoryPath, filesInDirectory, libraryEntity);
            }
        }
    }

    private Optional<BookEntity> findExistingBookInDirectory(Path directoryPath, LibraryEntity libraryEntity) {
        // Find books in all library paths for this library
        return libraryEntity.getLibraryPaths().stream()
                .flatMap(libPath -> {
                    String filesSearchPath = Path.of(libPath.getPath())
                            .relativize(directoryPath)
                            .toString()
                            .replace("\\", "/");
                    return bookRepository
                            .findAllByLibraryPathIdAndFileSubPathStartingWith(libPath.getId(), filesSearchPath)
                            .stream();
                })
                .filter(book -> book.getFullFilePath().getParent().equals(directoryPath))
                .findFirst();
    }

    private Optional<BookEntity> findBookInParentDirectory(Path directoryPath, LibraryEntity libraryEntity) {
        Path parent = directoryPath.getParent();
        LibraryPathEntity directoryLibraryPathEntity = libraryEntity.getLibraryPaths().stream()
                .filter(libPath -> directoryPath.startsWith(libPath.getPath()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No library path found for directory: " + directoryPath));
        Path directoryLibraryPath = Path.of(directoryLibraryPathEntity.getPath());

        while (parent != null) {
            final String parentPath = directoryLibraryPath
                    .relativize(parent)
                    .toString()
                    .replace("\\", "/");

            Optional<BookEntity> parentBook =
                    bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(
                            directoryLibraryPathEntity.getId(), parentPath).stream()
                    .filter(book -> book.getFileSubPath().equals(parentPath))
                    .findFirst();
            if (parentBook.isPresent()) {
                return parentBook;
            }
            parent = parent.getParent();
        }

        return Optional.empty();
    }

    private void processAdditionalFilesForExistingBook(BookEntity existingBook, List<LibraryFile> filesInDirectory) {
        for (LibraryFile file : filesInDirectory) {
            // Skip if this is the main book file
            if (file.getFileName().equals(existingBook.getFileName())) {
                continue;
            }

            Optional<BookFileExtension> extension = BookFileExtension.fromFileName(file.getFileName());
            AdditionalFileType fileType = extension.isPresent() ?
                    AdditionalFileType.ALTERNATIVE_FORMAT : AdditionalFileType.SUPPLEMENTARY;

            createAdditionalFileIfNotExists(existingBook, file, fileType);
        }
    }

    private void processSupplementaryFilesForParentBook(BookEntity parentBook, List<LibraryFile> filesInDirectory) {
        for (LibraryFile file : filesInDirectory) {
            createAdditionalFileIfNotExists(parentBook, file, AdditionalFileType.SUPPLEMENTARY);
        }
    }

    private void createNewBookFromDirectory(Path directoryPath, List<LibraryFile> filesInDirectory, LibraryEntity libraryEntity) {
        // Find the best candidate for the main book file
        Optional<LibraryFile> mainBookFile = findBestMainBookFile(filesInDirectory, libraryEntity);

        if (mainBookFile.isEmpty()) {
            log.debug("No suitable book file found in directory: {}", directoryPath);
            return;
        }

        LibraryFile bookFile = mainBookFile.get();

        try {
            log.info("Creating new book from file: {}", bookFile.getFileName());

            // Create the main book
            BookFileProcessor processor = bookFileProcessorRegistry.getProcessorOrThrow(bookFile.getBookFileType());
            Book book = processor.processFile(bookFile);

            if (book != null) {
                // Send notifications
                notificationService.sendMessage(
                        com.adityachandel.booklore.model.websocket.Topic.BOOK_ADD,
                        book
                );
                notificationService.sendMessage(
                        com.adityachandel.booklore.model.websocket.Topic.LOG,
                        com.adityachandel.booklore.model.websocket.LogNotification.createLogNotification(
                                "Book added: " + book.getFileName()
                        )
                );

                // Find the created book entity
                BookEntity bookEntity = bookRepository.getReferenceById(book.getId());
                if (bookEntity.getFullFilePath().equals(bookFile.getFullPath())) {
                    log.info("Successfully created new book: {}", bookEntity.getFileName());
                } else {
                    log.warn("Found duplicate book with different path: {} vs {}",
                            bookEntity.getFullFilePath(), bookFile.getFullPath());
                }

                // Process remaining files as additional files
                processAdditionalFilesForNewBook(bookEntity, filesInDirectory, bookFile);
            } else {
                log.warn("Book processor returned null for file: {}", bookFile.getFileName());
            }
        } catch (Exception e) {
            log.error("Error processing book file {}: {}", bookFile.getFileName(), e.getMessage(), e);
        }
    }

    private Optional<LibraryFile> findBestMainBookFile(List<LibraryFile> filesInDirectory, LibraryEntity libraryEntity) {
        List<LibraryFile> bookFiles = filesInDirectory.stream()
                .filter(file -> BookFileExtension.fromFileName(file.getFileName()).isPresent())
                .toList();

        if (bookFiles.isEmpty()) {
            return Optional.empty();
        }

        // If a library has a default book format preference, try to find a file with that format first
        if (libraryEntity.getDefaultBookFormat() != null) {
            Optional<LibraryFile> preferredFormatFile = bookFiles.stream()
                    .filter(file -> {
                        Optional<BookFileExtension> extension = BookFileExtension.fromFileName(file.getFileName());
                        return extension.isPresent() && extension.get().getType() == libraryEntity.getDefaultBookFormat();
                    })
                    .findFirst();

            if (preferredFormatFile.isPresent()) {
                log.debug("Selected book file based on default format {}: {}",
                        libraryEntity.getDefaultBookFormat(), preferredFormatFile.get().getFileName());
                return preferredFormatFile;
            }
        }

        // Fallback to a default priority order: PDF > EPUB > CBZ > CBR > CB7
        Optional<LibraryFile> defaultPriorityFile = bookFiles.stream()
                .min(Comparator.comparingInt(f -> f.getBookFileType().ordinal()));

        defaultPriorityFile.ifPresent(libraryFile ->
                log.debug("Selected book file based on default priority: {}", libraryFile.getFileName()));

        return defaultPriorityFile;
    }

    private void processAdditionalFilesForNewBook(BookEntity bookEntity, List<LibraryFile> filesInDirectory, LibraryFile mainBookFile) {
        for (LibraryFile file : filesInDirectory) {
            // Skip the main book file
            if (file.equals(mainBookFile)) {
                continue;
            }

            Optional<BookFileExtension> extension = BookFileExtension.fromFileName(file.getFileName());
            AdditionalFileType fileType = extension.isPresent() ?
                    AdditionalFileType.ALTERNATIVE_FORMAT : AdditionalFileType.SUPPLEMENTARY;

            createAdditionalFileIfNotExists(bookEntity, file, fileType);
        }
    }

    private void createAdditionalFileIfNotExists(BookEntity bookEntity, LibraryFile file, AdditionalFileType fileType) {
        // Check if an additional file already exists
        Optional<BookAdditionalFileEntity> existingFile = bookAdditionalFileRepository
                .findByLibraryPath_IdAndFileSubPathAndFileName(
                        file.getLibraryPathEntity().getId(), file.getFileSubPath(), file.getFileName());

        if (existingFile.isPresent()) {
            log.debug("Additional file already exists: {}", file.getFileName());
            return;
        }

        try {
            log.debug("Creating additional file: {} (type: {})", file.getFileName(), fileType);

            // Create new additional file
            String hash = FileFingerprint.generateHash(file.getFullPath());
            BookAdditionalFileEntity additionalFile = BookAdditionalFileEntity.builder()
                    .book(bookEntity)
                    .fileName(file.getFileName())
                    .fileSubPath(file.getFileSubPath())
                    .additionalFileType(fileType)
                    .fileSizeKb(FileUtils.getFileSizeInKb(file.getFullPath()))
                    .initialHash(hash)
                    .currentHash(hash)
                    .addedOn(java.time.Instant.now())
                    .build();

            bookAdditionalFileRepository.save(additionalFile);
            log.debug("Successfully created additional file: {}", file.getFileName());
        } catch (Exception e) {
            log.error("Error creating additional file {}: {}", file.getFileName(), e.getMessage(), e);
        }
    }
}
