package org.booklore.service.fileprocessor;

import org.booklore.mapper.BookMapper;
import org.booklore.model.FileProcessResult;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.FileProcessStatus;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.util.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

@Slf4j
public abstract class AbstractFileProcessor implements BookFileProcessor {

    protected final BookRepository bookRepository;
    protected final BookAdditionalFileRepository bookAdditionalFileRepository;
    protected final BookCreatorService bookCreatorService;
    protected final BookMapper bookMapper;
    protected final MetadataMatchService metadataMatchService;
    protected final FileService fileService;
    protected final SidecarMetadataWriter sidecarMetadataWriter;


    protected AbstractFileProcessor(BookRepository bookRepository,
                                    BookAdditionalFileRepository bookAdditionalFileRepository,
                                    BookCreatorService bookCreatorService,
                                    BookMapper bookMapper,
                                    FileService fileService,
                                    MetadataMatchService metadataMatchService,
                                    SidecarMetadataWriter sidecarMetadataWriter) {
        this.bookRepository = bookRepository;
        this.bookAdditionalFileRepository = bookAdditionalFileRepository;
        this.bookCreatorService = bookCreatorService;
        this.bookMapper = bookMapper;
        this.metadataMatchService = metadataMatchService;
        this.fileService = fileService;
        this.sidecarMetadataWriter = sidecarMetadataWriter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public FileProcessResult processFile(LibraryFile libraryFile) {
        Path path = libraryFile.getFullPath();
        String hash = libraryFile.isFolderBased()
                ? FileFingerprint.generateFolderHash(path)
                : FileFingerprint.generateHash(path);
        Book book = createAndMapBook(libraryFile, hash);
        return new FileProcessResult(book, FileProcessStatus.NEW);
    }

    private Book createAndMapBook(LibraryFile libraryFile, String hash) {
        BookEntity entity = processNewFile(libraryFile);
        entity.getPrimaryBookFile().setCurrentHash(hash);
        entity.setMetadataMatchScore(metadataMatchService.calculateMatchScore(entity));
        bookCreatorService.saveConnections(entity);

        if (sidecarMetadataWriter.isWriteOnScanEnabled()) {
            try {
                sidecarMetadataWriter.writeSidecarMetadata(entity);
            } catch (Exception e) {
                log.warn("Failed to write sidecar metadata for book ID {}: {}", entity.getId(), e.getMessage());
            }
        }

        return bookMapper.toBook(entity);
    }

    protected abstract BookEntity processNewFile(LibraryFile libraryFile);
}