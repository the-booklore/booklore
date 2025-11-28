package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.adityachandel.booklore.service.event.BookEventBroadcaster;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
public class FileAsBookProcessor implements LibraryFileProcessor {

    private final BookEventBroadcaster bookEventBroadcaster;
    private final BookFileProcessorRegistry processorRegistry;

    @Override
    public LibraryScanMode getScanMode() {
        return LibraryScanMode.FILE_AS_BOOK;
    }

    @Override
    @Transactional
    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        for (LibraryFile libraryFile : libraryFiles) {
            log.info("Processing file: {}", libraryFile.getFileName());

            FileProcessResult result = processLibraryFile(libraryFile);

            if (result != null) {
                bookEventBroadcaster.broadcastBookAddEvent(result.getBook());
                log.debug("Processed file: {}", libraryFile.getFileName());
            }
        }

        log.info("Finished processing library '{}'", libraryEntity.getName());
    }

    @Transactional
    protected FileProcessResult processLibraryFile(LibraryFile libraryFile) {
        BookFileType type = libraryFile.getBookFileType();
        if (type == null) {
            log.warn("Unsupported file type for file: {}", libraryFile.getFileName());
            return null;
        }

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(type);
        return processor.processFile(libraryFile);
    }

}