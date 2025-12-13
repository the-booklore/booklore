package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.FileProcessStatus;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.ShelfRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("BookImportService Tests")
class BookImportServiceTest {

    private LibraryRepository libraryRepository;
    private BookFileProcessorRegistry processorRegistry;
    private BookRepository bookRepository;
    private NotificationService notificationService;
    private MetadataRefreshService metadataRefreshService;
    private BookMapper bookMapper;
    private ShelfRepository shelfRepository;

    private BookImportService bookImportService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        libraryRepository = mock(LibraryRepository.class);
        processorRegistry = mock(BookFileProcessorRegistry.class);
        bookRepository = mock(BookRepository.class);
        notificationService = mock(NotificationService.class);
        metadataRefreshService = mock(MetadataRefreshService.class);
        bookMapper = mock(BookMapper.class);
        shelfRepository = mock(ShelfRepository.class);

        bookImportService = new BookImportService(libraryRepository, processorRegistry, bookRepository,
                notificationService, metadataRefreshService, bookMapper, shelfRepository);
    }

    @Test
    void importFileToLibrary_happyPath_importsAndAppliesMetadataAndAddsToShelf(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        Path libDir = tempDir.resolve("lib");
        Files.createDirectories(libDir);
        Path filePath = Files.createFile(libDir.resolve("test-book.epub"));
        Files.writeString(filePath, "content");
        File file = filePath.toFile();

        LibraryEntity library = new LibraryEntity();
        library.setId(1L);
        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setId(2L);
        libPath.setPath(libDir.toString());
        libPath.setLibrary(library);
        library.setLibraryPaths(List.of(libPath));

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));

        BookFileProcessor processor = mock(BookFileProcessor.class);
        when(processor.getSupportedTypes()).thenReturn(List.of(BookFileType.EPUB));
        Book outputDto = Book.builder().id(42L).fileName("test-book.epub").build();
        FileProcessResult result = new FileProcessResult(outputDto, FileProcessStatus.NEW);
        when(processor.processFile(any(LibraryFile.class))).thenReturn(result);
        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(processor);

        BookEntity storedEntity = new BookEntity();
        storedEntity.setId(42L);
        when(bookRepository.findById(42L)).thenReturn(Optional.of(storedEntity));
        when(bookMapper.toBook(storedEntity)).thenReturn(outputDto);

        BookMetadata metadata = new BookMetadata();
        metadata.setTitle("Imported Title");
        Long shelfId = 77L;
        ShelfEntity shelf = new ShelfEntity();
        shelf.setId(shelfId);
        when(shelfRepository.findById(shelfId)).thenReturn(Optional.of(shelf));
        Book imported = bookImportService.importFileToLibrary(file, 1L, 2L, metadata, shelfId);
        assertThat(imported).isNotNull();
        assertThat(imported.getId()).isEqualTo(42L);
        verify(processor).processFile(argThat(lf -> lf.getFileName().equals("test-book.epub") && lf.getLibraryPathEntity().getId().equals(2L)));
        verify(metadataRefreshService).updateBookMetadata(any());
        verify(bookRepository).save(storedEntity);
        assertThat(storedEntity.getShelves()).isNotNull();
        assertThat(storedEntity.getShelves()).extracting(ShelfEntity::getId).contains(shelfId);
        assertThat(imported.getId()).isEqualTo(42L);
    }
}
