package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.enums.FileProcessStatus;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
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
import org.mockito.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

        Book book1 = Book.builder().id(1L).fileName("book1.epub").bookType(BookFileType.EPUB).build();
        Book book2 = Book.builder().id(2L).fileName("book2.pdf").bookType(BookFileType.PDF).build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(processorRegistry.getProcessorOrThrow(BookFileType.PDF)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(file1)).thenReturn(new FileProcessResult(book1, FileProcessStatus.NEW));
        when(bookFileProcessor.processFile(file2)).thenReturn(new FileProcessResult(book2, FileProcessStatus.NEW));

        fileAsBookProcessor.processLibraryFiles(List.of(file1, file2), libraryEntity);

        verify(bookEventBroadcaster, times(2)).broadcastBookAddEvent(any());
        verify(bookFileProcessor, times(2)).processFile(any());
    }

    @Test
    void processLibraryFiles_shouldGroupSameBaseNameFilesAndAttachAdditional() {
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setFormatPriority(List.of(BookFileType.EPUB));
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath("/library/path");

        LibraryFile epubFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("The Hobbit.epub")
                .fileSubPath("books")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile pdfFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("The Hobbit.pdf")
                .fileSubPath("books")
                .bookFileType(BookFileType.PDF)
                .build();

        Book primaryBook = Book.builder().id(1L).fileName("The Hobbit.epub").bookType(BookFileType.EPUB).build();

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookFileEntity primaryBookFile = new BookFileEntity();
        primaryBookFile.setFileName("The Hobbit.epub");
        bookEntity.setBookFiles(List.of(primaryBookFile));

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(epubFile)).thenReturn(new FileProcessResult(primaryBook, FileProcessStatus.NEW));
        when(bookRepository.getReferenceById(1L)).thenReturn(bookEntity);
        when(bookAdditionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName(1L, "books", "The Hobbit.pdf"))
                .thenReturn(Optional.empty());

        fileAsBookProcessor.processLibraryFiles(List.of(epubFile, pdfFile), libraryEntity);

        // Only the primary should be processed via bookFileProcessor
        verify(bookFileProcessor, times(1)).processFile(epubFile);
        verify(bookFileProcessor, never()).processFile(pdfFile);
        // The additional file should be saved
        verify(bookAdditionalFileRepository).save(any(BookFileEntity.class));
        // Only one broadcast event
        verify(bookEventBroadcaster, times(1)).broadcastBookAddEvent(any());
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
    void processLibraryFiles_shouldGroupFormatIndicatorVariants() {
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setFormatPriority(List.of(BookFileType.EPUB));
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath("/library/path");

        // "The Hobbit (PDF).pdf" should normalize to "the hobbit" and group with "The Hobbit.epub"
        LibraryFile epubFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("The Hobbit.epub")
                .fileSubPath("books")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile pdfFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("The Hobbit (PDF).pdf")
                .fileSubPath("books")
                .bookFileType(BookFileType.PDF)
                .build();

        Book primaryBook = Book.builder().id(1L).fileName("The Hobbit.epub").bookType(BookFileType.EPUB).build();

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookFileEntity primaryBookFile = new BookFileEntity();
        primaryBookFile.setFileName("The Hobbit.epub");
        bookEntity.setBookFiles(List.of(primaryBookFile));

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(epubFile)).thenReturn(new FileProcessResult(primaryBook, FileProcessStatus.NEW));
        when(bookRepository.getReferenceById(1L)).thenReturn(bookEntity);
        when(bookAdditionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName(1L, "books", "The Hobbit (PDF).pdf"))
                .thenReturn(Optional.empty());

        fileAsBookProcessor.processLibraryFiles(List.of(epubFile, pdfFile), libraryEntity);

        verify(bookFileProcessor, times(1)).processFile(epubFile);
        verify(bookAdditionalFileRepository).save(any(BookFileEntity.class));
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

        Book book1 = Book.builder().id(1L).fileName("book.epub").bookType(BookFileType.EPUB).build();
        Book book2 = Book.builder().id(2L).fileName("book.pdf").bookType(BookFileType.PDF).build();

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
