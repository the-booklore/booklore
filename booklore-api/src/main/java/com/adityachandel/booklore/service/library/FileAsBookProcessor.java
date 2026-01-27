package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.event.BookEventBroadcaster;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.kobo.KoboAutoShelfService;
import com.adityachandel.booklore.util.BookFileGroupingUtils;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class FileAsBookProcessor {

    private final BookEventBroadcaster bookEventBroadcaster;
    private final BookFileProcessorRegistry processorRegistry;
    private final KoboAutoShelfService koboAutoShelfService;
    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;

    @Transactional
    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(libraryFiles);
        processLibraryFilesGrouped(groups, libraryEntity);
    }

    /**
     * Process pre-grouped library files. Use this when grouping has already been done
     * (e.g., by BookGroupingService during rescan).
     */
    @Transactional
    public void processLibraryFilesGrouped(Map<String, List<LibraryFile>> groups, LibraryEntity libraryEntity) {
        for (Map.Entry<String, List<LibraryFile>> entry : groups.entrySet()) {
            processGroupWithErrorHandling(entry.getValue(), libraryEntity);
        }
        log.info("Finished processing library '{}'", libraryEntity.getName());
    }

    private void processGroupWithErrorHandling(List<LibraryFile> group, LibraryEntity libraryEntity) {
        try {
            processGroup(group, libraryEntity);
        } catch (Exception e) {
            String fileNames = group.stream().map(LibraryFile::getFileName).toList().toString();
            log.error("Failed to process file group {}: {}", fileNames, e.getMessage());
        }
    }

    private void processGroup(List<LibraryFile> group, LibraryEntity libraryEntity) {
        Optional<LibraryFile> primaryFile = findBestPrimaryFile(group, libraryEntity);
        if (primaryFile.isEmpty()) {
            log.warn("No suitable book file found in group");
            return;
        }

        LibraryFile primary = primaryFile.get();
        log.info("Processing file: {}", primary.getFileName());

        BookFileType type = primary.getBookFileType();
        if (type == null) {
            log.warn("Unsupported file type for file: {}", primary.getFileName());
            return;
        }

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(type);
        FileProcessResult result = processor.processFile(primary);

        if (result == null || result.getBook() == null) {
            log.warn("Failed to process primary file: {}", primary.getFileName());
            return;
        }

        bookEventBroadcaster.broadcastBookAddEvent(result.getBook());
        koboAutoShelfService.autoAddBookToKoboShelves(result.getBook().getId());

        List<LibraryFile> additionalFiles = group.stream()
                .filter(f -> !f.equals(primary))
                .toList();

        if (!additionalFiles.isEmpty()) {
            BookEntity bookEntity = bookRepository.getReferenceById(result.getBook().getId());
            for (LibraryFile additionalFile : additionalFiles) {
                createAdditionalBookFile(bookEntity, additionalFile);
            }
        }
    }

    private Optional<LibraryFile> findBestPrimaryFile(List<LibraryFile> group, LibraryEntity libraryEntity) {
        List<BookFileType> formatPriority = libraryEntity.getFormatPriority();
        return group.stream()
                .filter(f -> f.getBookFileType() != null)
                .min(Comparator.comparingInt(f -> {
                    BookFileType bookFileType = f.getBookFileType();
                    if (formatPriority != null && !formatPriority.isEmpty()) {
                        int index = formatPriority.indexOf(bookFileType);
                        return index >= 0 ? index : Integer.MAX_VALUE;
                    }
                    return bookFileType.ordinal();
                }));
    }

    private void createAdditionalBookFile(BookEntity bookEntity, LibraryFile file) {
        Optional<BookFileEntity> existing = bookAdditionalFileRepository
                .findByLibraryPath_IdAndFileSubPathAndFileName(
                        file.getLibraryPathEntity().getId(), file.getFileSubPath(), file.getFileName());

        if (existing.isPresent()) {
            log.debug("Additional file already exists: {}", file.getFileName());
            return;
        }

        // Handle folder-based audiobooks vs regular files
        String hash;
        Long fileSizeKb;
        if (file.isFolderBased()) {
            hash = FileFingerprint.generateFolderHash(file.getFullPath());
            fileSizeKb = FileUtils.getFolderSizeInKb(file.getFullPath());
        } else {
            hash = FileFingerprint.generateHash(file.getFullPath());
            fileSizeKb = FileUtils.getFileSizeInKb(file.getFullPath());
        }

        BookFileEntity additionalFile = BookFileEntity.builder()
                .book(bookEntity)
                .fileName(file.getFileName())
                .fileSubPath(file.getFileSubPath())
                .isBookFormat(true)
                .folderBased(file.isFolderBased())
                .bookType(file.getBookFileType())
                .fileSizeKb(fileSizeKb)
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(Instant.now())
                .build();

        try {
            bookAdditionalFileRepository.save(additionalFile);
            log.info("Attached additional format {} to book: {}", file.getFileName(), bookEntity.getPrimaryBookFile().getFileName());
        } catch (Exception e) {
            log.error("Error creating additional file {}: {}", file.getFileName(), e.getMessage());
        }
    }
}
