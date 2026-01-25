package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookFile;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.FileProcessStatus;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.event.BookEventBroadcaster;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.kobo.KoboAutoShelfService;
import com.adityachandel.booklore.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileAsBookProcessorTest {

    @Mock
    private BookEventBroadcaster bookEventBroadcaster;

    @Mock
    private BookFileProcessorRegistry processorRegistry;

    @Mock
    private KoboAutoShelfService koboAutoShelfService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookAdditionalFileRepository bookAdditionalFileRepository;

    @Mock
    private BookFileProcessor bookFileProcessor;

    private FileAsBookProcessor fileAsBookProcessor;

    private AutoCloseable mocks;
    private MockedStatic<FileFingerprint> fileFingerprintMock;
    private MockedStatic<FileUtils> fileUtilsMock;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        fileAsBookProcessor = new FileAsBookProcessor(
                bookEventBroadcaster,
                processorRegistry,
                koboAutoShelfService,
                bookRepository,
                bookAdditionalFileRepository
        );
        fileFingerprintMock = mockStatic(FileFingerprint.class);
        fileFingerprintMock.when(() -> FileFingerprint.generateHash(any(Path.class))).thenReturn("testhash");
        fileUtilsMock = mockStatic(FileUtils.class);
        fileUtilsMock.when(() -> FileUtils.getFileSizeInKb(any(Path.class))).thenReturn(100L);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (fileFingerprintMock != null) fileFingerprintMock.close();
        if (fileUtilsMock != null) fileUtilsMock.close();
        if (mocks != null) mocks.close();
    }

    @Test
    void staticMocksShouldWork() {
        // Verify static mocks are working
        Path testPath = Path.of("/test/path");
        String hash = FileFingerprint.generateHash(testPath);
        Long size = FileUtils.getFileSizeInKb(testPath);
        assert hash.equals("testhash") : "Expected 'testhash' but got: " + hash;
        assert size.equals(100L) : "Expected 100L but got: " + size;
    }

    @Test
    void processLibraryFiles_shouldProcessDifferentNamedFilesSeparately() {
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath("/library/path");

        LibraryFile file1 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book1.epub")
                .fileSubPath("books")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book2.pdf")
                .fileSubPath("books")
                .bookFileType(BookFileType.PDF)
                .build();

        Book book1 = Book.builder().id(1L).primaryFile(BookFile.builder().fileName("book1.epub").bookType(BookFileType.EPUB).build()).build();
        Book book2 = Book.builder().id(2L).primaryFile(BookFile.builder().fileName("book2.pdf").bookType(BookFileType.PDF).build()).build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(processorRegistry.getProcessorOrThrow(BookFileType.PDF)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(file1)).thenReturn(new FileProcessResult(book1, FileProcessStatus.NEW));
        when(bookFileProcessor.processFile(file2)).thenReturn(new FileProcessResult(book2, FileProcessStatus.NEW));

        fileAsBookProcessor.processLibraryFiles(List.of(file1, file2), libraryEntity);

        verify(bookEventBroadcaster, times(2)).broadcastBookAddEvent(any());
        verify(bookFileProcessor, times(2)).processFile(any());
    }

    @Test
    void processLibraryFiles_shouldHandleEmptyList() {
        LibraryEntity libraryEntity = new LibraryEntity();

        fileAsBookProcessor.processLibraryFiles(new ArrayList<>(), libraryEntity);

        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
        verify(processorRegistry, never()).getProcessorOrThrow(any());
    }

    @Test
    void processLibraryFiles_shouldSkipFilesWithNullBookFileType() {
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath("/library/path");

        LibraryFile invalidFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("document.txt")
                .fileSubPath("docs")
                .bookFileType(null)
                .build();

        fileAsBookProcessor.processLibraryFiles(List.of(invalidFile), libraryEntity);

        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
        verify(processorRegistry, never()).getProcessorOrThrow(any());
    }

    @Test
    void processLibraryFiles_shouldNotBroadcastWhenProcessorReturnsNull() {
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath("/library/path");

        LibraryFile file = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.epub")
                .fileSubPath("books")
                .bookFileType(BookFileType.EPUB)
                .build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(file)).thenReturn(null);

        fileAsBookProcessor.processLibraryFiles(List.of(file), libraryEntity);

        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
    }

    @Test
    void processLibraryFiles_shouldNotGroupFilesInDifferentDirectories() {
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath("/library/path");

        LibraryFile file1 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.epub")
                .fileSubPath("dir1")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.pdf")
                .fileSubPath("dir2")
                .bookFileType(BookFileType.PDF)
                .build();

        Book book1 = Book.builder().id(1L).primaryFile(BookFile.builder().fileName("book.epub").bookType(BookFileType.EPUB).build()).build();
        Book book2 = Book.builder().id(2L).primaryFile(BookFile.builder().fileName("book.pdf").bookType(BookFileType.PDF).build()).build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(processorRegistry.getProcessorOrThrow(BookFileType.PDF)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(file1)).thenReturn(new FileProcessResult(book1, FileProcessStatus.NEW));
        when(bookFileProcessor.processFile(file2)).thenReturn(new FileProcessResult(book2, FileProcessStatus.NEW));

        fileAsBookProcessor.processLibraryFiles(List.of(file1, file2), libraryEntity);

        // Both should be processed as separate books
        verify(bookFileProcessor, times(2)).processFile(any());
        verify(bookEventBroadcaster, times(2)).broadcastBookAddEvent(any());
    }
}
