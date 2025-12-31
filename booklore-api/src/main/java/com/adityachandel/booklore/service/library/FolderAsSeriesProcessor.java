package com.adityachandel.booklore.service.library;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.adityachandel.booklore.service.event.BookEventBroadcaster;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.kobo.KoboAutoShelfService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Component
@Slf4j
public class FolderAsSeriesProcessor implements LibraryFileProcessor {

    private final BookEventBroadcaster bookEventBroadcaster;
    private final BookFileProcessorRegistry processorRegistry;
    private final KoboAutoShelfService koboAutoShelfService;

    @Override
    public LibraryScanMode getScanMode() {
        return LibraryScanMode.FOLDER_AS_SERIES;
    }

    @Override
    @Transactional
    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        for (LibraryFile libraryFile : libraryFiles) {
            processFileWithErrorHandling(libraryFile);
        }
        log.info("Finished processing library '{}'", libraryEntity.getName());
    }

    private void processFileWithErrorHandling(LibraryFile libraryFile) {
        log.info("Processing file: {}", libraryFile.getFileName());
        try {
            FileProcessResult result = processLibraryFile(libraryFile);
            if (result != null) {
                bookEventBroadcaster.broadcastBookAddEvent(result.getBook());
                koboAutoShelfService.autoAddBookToKoboShelves(result.getBook().getId());
            }
        } catch (Exception e) {
            log.error("Failed to process file '{}': {}", libraryFile.getFileName(), e.getMessage());
        }
    }

    @Transactional
    protected FileProcessResult processLibraryFile(LibraryFile libraryFile) {
        BookFileType type = libraryFile.getBookFileType();
        if (type == null) {
            log.warn("Unsupported file type for file: {}", libraryFile.getFileName());
            return null;
        }
        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(type);
        FileProcessResult result = processor.processFile(libraryFile);
        String subpath = libraryFile.getFileSubPath();
        if (subpath == null || subpath.isEmpty()) {
            result.getBook().getMetadata().setSeriesName(null);
        } else {
            result.getBook().getMetadata().setSeriesName(libraryFile.getFullPath().getParent().getFileName().toString());
        }
        return result;
    }

}