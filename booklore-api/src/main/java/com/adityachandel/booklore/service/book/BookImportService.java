package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.ShelfRepository;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class BookImportService {

    private final LibraryRepository libraryRepository;
    private final BookFileProcessorRegistry processorRegistry;
    private final BookRepository bookRepository;
    private final NotificationService notificationService;
    private final MetadataRefreshService metadataRefreshService;
    private final BookMapper bookMapper;
    private final ShelfRepository shelfRepository;

    /**
     * Import a single file into the specified library / libraryPath.
     * This reuses the same processors used by Bookdrop and mirrors the important
     * post-processing steps (notify, optional metadata application).
     *
     * @param file the file already moved into the target library path
     * @param libraryId target library id
     * @param libraryPathId target library path id
     * @param metadata optional metadata to apply after import (can be null)
     * @return the imported Book DTO
     */
    public Book importFileToLibrary(File file, Long libraryId, Long libraryPathId, BookMetadata metadata, Long shelfId) {
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        LibraryPathEntity libPath = library.getLibraryPaths().stream()
                .filter(p -> p.getId().equals(libraryPathId))
                .findFirst()
                .orElseThrow(() -> ApiError.INVALID_LIBRARY_PATH.createException(libraryId));

        String fileName = file.getName();
        Path filePath = file.toPath();

        BookFileExtension ext = BookFileExtension.fromFileName(fileName)
                .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension: " + fileName));
        BookFileType type = ext.getType();

        String fileSubPath = FileUtils.getRelativeSubPath(libPath.getPath(), filePath);

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(library)
                .libraryPathEntity(libPath)
                .fileSubPath(fileSubPath)
                .fileName(fileName)
                .bookFileType(type)
                .build();

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(type);

        var processResult = processor.processFile(libraryFile);

        Book bookDto = processResult.getBook();

        // Ensure we have the persisted entity available
        BookEntity bookEntity = bookRepository.findById(bookDto.getId())
                .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("Book ID missing after import"));

        // Notify listeners about new book
        try {
            notificationService.sendMessage(Topic.BOOK_ADD, bookDto);
        } catch (Exception e) {
            log.warn("Failed to send BOOK_ADD notification for book {}: {}", bookDto.getId(), e.getMessage());
        }

        // If optional metadata provided, apply it using existing metadata flow
        if (metadata != null) {
            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(bookEntity)
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(metadata).build())
                    .updateThumbnail(metadata.getThumbnailUrl() != null)
                    .mergeCategories(false)
                    .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                    .mergeMoods(true)
                    .mergeTags(true)
                    .build();

            metadataRefreshService.updateBookMetadata(context);
        }

        // If a shelfId was provided, add the imported book to that shelf
        if (shelfId != null) {
            try {
                ShelfEntity shelfEntity = shelfRepository.findById(shelfId).orElse(null);
                if (shelfEntity != null) {
                    if (bookEntity.getShelves() == null) {
                        bookEntity.setShelves(new java.util.HashSet<>());
                    }
                    bookEntity.getShelves().add(shelfEntity);
                    bookRepository.save(bookEntity);
                }
            } catch (Exception e) {
                log.warn("Failed to add imported book {} to shelf {}: {}", bookEntity.getId(), shelfId, e.getMessage());
            }
        }

        // Re-map the (potentially updated) entity to DTO to include shelf changes
        return bookMapper.toBook(bookEntity);
    }
}
