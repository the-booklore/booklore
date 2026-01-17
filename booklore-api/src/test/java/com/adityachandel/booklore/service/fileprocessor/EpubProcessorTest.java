package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.book.BookCreatorService;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.service.metadata.extractor.EpubMetadataExtractor;
import com.adityachandel.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EpubProcessorTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock private BookCreatorService bookCreatorService;
    @Mock private BookMapper bookMapper;
    @Mock private FileService fileService;
    @Mock private MetadataMatchService metadataMatchService;
    @Mock private EpubMetadataExtractor epubMetadataExtractor;
    @Mock private AppSettingService appSettingService;

    private EpubProcessor epubProcessor;

    @BeforeEach
    void setUp() {
        epubProcessor = new EpubProcessor(
                bookRepository,
                bookAdditionalFileRepository,
                bookCreatorService,
                bookMapper,
                fileService,
                metadataMatchService,
                epubMetadataExtractor,
                appSettingService
        );
    }

    @Test
    void processNewFile_ShouldExtractMetadataAndNotLockIt() {
        LibraryFile libraryFile = LibraryFile.builder()
                .fileName("test.epub")
                .build();

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setFileName("test.epub");
        primaryFile.setFileSubPath("books");
        bookEntity.getBookFiles().add(primaryFile);
        
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath("/tmp/library");
        bookEntity.setLibraryPath(libraryPath);
        
        bookEntity.setMetadata(new BookMetadataEntity());

        BookMetadata extractedMetadata = BookMetadata.builder()
                .title("Extracted Title")
                .authors(Set.of("Extracted Author"))
                .build();

        when(bookCreatorService.createShellBook(any(LibraryFile.class), eq(BookFileType.EPUB))).thenReturn(bookEntity);
        when(epubMetadataExtractor.extractMetadata(any(File.class))).thenReturn(extractedMetadata);
        
        epubProcessor.processNewFile(libraryFile);

        BookMetadataEntity metadata = bookEntity.getMetadata();
        assertEquals("Extracted Title", metadata.getTitle());
        
        assertFalse(Boolean.TRUE.equals(metadata.getTitleLocked()));
        assertFalse(Boolean.TRUE.equals(metadata.getAuthorsLocked()));
    }
}