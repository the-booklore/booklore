package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.task.options.RescanLibraryContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryProcessingServiceTest {

    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock
    private LibraryFileProcessorRegistry fileProcessorRegistry;
    @Mock
    private BookRestorationService bookRestorationService;
    @Mock
    private BookDeletionService bookDeletionService;
    @Mock
    private LibraryFileHelper libraryFileHelper;
    @Mock
    private EntityManager entityManager;
    @Mock
    private LibraryFileProcessor libraryFileProcessor;

    private LibraryProcessingService libraryProcessingService;

    @BeforeEach
    void setUp() {
        libraryProcessingService = new LibraryProcessingService(
                libraryRepository,
                notificationService,
                bookAdditionalFileRepository,
                fileProcessorRegistry,
                bookRestorationService,
                bookDeletionService,
                libraryFileHelper,
                entityManager
        );
    }

    @Test
    void processLibrary_shouldOnlyProcessNewFiles() throws IOException {
        long libraryId = 1L;
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setScanMode(LibraryScanMode.FILE_AS_BOOK);

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath("/library");

        BookEntity existingBook = new BookEntity();
        existingBook.setLibraryPath(pathEntity);
        BookFileEntity existingBookFile = new BookFileEntity();
        existingBookFile.setBook(existingBook);
        existingBook.setBookFiles(List.of(existingBookFile));
        existingBook.getPrimaryBookFile().setFileSubPath("");
        existingBook.getPrimaryBookFile().setFileName("book1.epub");
        libraryEntity.setBookEntities(List.of(existingBook));

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(fileProcessorRegistry.getProcessor(libraryEntity)).thenReturn(libraryFileProcessor);

        // Library files found on disk (1 existing, 1 new)
        LibraryFile existingFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book1.epub")
                .build();

        LibraryFile newFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book2.epub")
                .build();

        when(libraryFileHelper.getLibraryFiles(libraryEntity, libraryFileProcessor)).thenReturn(List.of(existingFile, newFile));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());

        libraryProcessingService.processLibrary(libraryId);

        ArgumentCaptor<List<LibraryFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(libraryFileProcessor).processLibraryFiles(captor.capture(), eq(libraryEntity));

        List<LibraryFile> processedFiles = captor.getValue();

        assertThat(processedFiles).hasSize(1);
        assertThat(processedFiles.getFirst().getFileName()).isEqualTo("book2.epub");
    }

    @Test
    void processLibrary_noNewFiles_shouldProcessNothing() throws IOException {
        long libraryId = 1L;
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setScanMode(LibraryScanMode.FILE_AS_BOOK);

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath("/library");

        BookEntity existingBook = new BookEntity();
        existingBook.setLibraryPath(pathEntity);
        BookFileEntity existingBookFile = new BookFileEntity();
        existingBookFile.setBook(existingBook);
        existingBook.setBookFiles(List.of(existingBookFile));
        existingBook.getPrimaryBookFile().setFileSubPath("");
        existingBook.getPrimaryBookFile().setFileName("book1.epub");
        libraryEntity.setBookEntities(List.of(existingBook));

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(fileProcessorRegistry.getProcessor(libraryEntity)).thenReturn(libraryFileProcessor);

        LibraryFile existingFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book1.epub")
                .build();

        when(libraryFileHelper.getLibraryFiles(libraryEntity, libraryFileProcessor)).thenReturn(List.of(existingFile));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());

        libraryProcessingService.processLibrary(libraryId);

        ArgumentCaptor<List<LibraryFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(libraryFileProcessor).processLibraryFiles(captor.capture(), eq(libraryEntity));

        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void processLibrary_allNewFiles_shouldProcessAll() throws IOException {
        long libraryId = 1L;
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setScanMode(LibraryScanMode.FILE_AS_BOOK);
        libraryEntity.setBookEntities(Collections.emptyList()); // No existing books

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath("/library");

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(fileProcessorRegistry.getProcessor(libraryEntity)).thenReturn(libraryFileProcessor);

        LibraryFile newFile1 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book1.epub")
                .build();
        LibraryFile newFile2 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book2.epub")
                .build();

        when(libraryFileHelper.getLibraryFiles(libraryEntity, libraryFileProcessor)).thenReturn(List.of(newFile1, newFile2));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());

        libraryProcessingService.processLibrary(libraryId);

        ArgumentCaptor<List<LibraryFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(libraryFileProcessor).processLibraryFiles(captor.capture(), eq(libraryEntity));

        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void processLibrary_newFileInSubdirectory_shouldProcess() throws IOException {
        long libraryId = 1L;
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setScanMode(LibraryScanMode.FILE_AS_BOOK);
        libraryEntity.setBookEntities(Collections.emptyList());

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath("/library");

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(fileProcessorRegistry.getProcessor(libraryEntity)).thenReturn(libraryFileProcessor);

        LibraryFile newFileInSub = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("fantasy")
                .fileName("book1.epub")
                .build();

        when(libraryFileHelper.getLibraryFiles(libraryEntity, libraryFileProcessor)).thenReturn(List.of(newFileInSub));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());

        libraryProcessingService.processLibrary(libraryId);

        ArgumentCaptor<List<LibraryFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(libraryFileProcessor).processLibraryFiles(captor.capture(), eq(libraryEntity));

        List<LibraryFile> processedFiles = captor.getValue();
        assertThat(processedFiles).hasSize(1);
        assertThat(processedFiles.getFirst().getFileName()).isEqualTo("book1.epub");
        assertThat(processedFiles.getFirst().getFileSubPath()).isEqualTo("fantasy");
    }

    @Test
    void processLibrary_additionalFile_shouldNotProcess() throws IOException {
        long libraryId = 1L;
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setScanMode(LibraryScanMode.FILE_AS_BOOK);
        libraryEntity.setBookEntities(Collections.emptyList());

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath("/library");

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(fileProcessorRegistry.getProcessor(libraryEntity)).thenReturn(libraryFileProcessor);

        // A file that exists as an additional file (e.g. cover.jpg, or a misidentified book)
        LibraryFile additionalFileAsLibraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("extra.pdf")
                .build();

        BookEntity parentBook = new BookEntity();
        parentBook.setLibraryPath(pathEntity);
        
        BookFileEntity additionalFileEntity = new BookFileEntity();
        additionalFileEntity.setBook(parentBook); // Links to library path
        additionalFileEntity.setFileSubPath("");
        additionalFileEntity.setFileName("extra.pdf");

        when(libraryFileHelper.getLibraryFiles(libraryEntity, libraryFileProcessor)).thenReturn(List.of(additionalFileAsLibraryFile));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(List.of(additionalFileEntity));

        libraryProcessingService.processLibrary(libraryId);

        ArgumentCaptor<List<LibraryFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(libraryFileProcessor).processLibraryFiles(captor.capture(), eq(libraryEntity));

        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void rescanLibrary_shouldNotDeleteNonBookFiles_whenProcessorDoesNotSupportSupplementaryFiles(@TempDir Path tempDir) throws IOException {
        long libraryId = 1L;
        Path accessiblePath = tempDir.resolve("library");
        Files.createDirectory(accessiblePath);

        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setScanMode(LibraryScanMode.FILE_AS_BOOK);

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath(accessiblePath.toString());
        libraryEntity.setLibraryPaths(List.of(pathEntity));

        BookEntity book = new BookEntity();
        book.setId(11L);
        book.setLibrary(libraryEntity);
        book.setLibraryPath(pathEntity);

        BookFileEntity epub = new BookFileEntity();
        epub.setId(1L);
        epub.setBook(book);
        epub.setFileSubPath("author/title");
        epub.setFileName("book.epub");
        epub.setBookFormat(true);

        BookFileEntity pdf = new BookFileEntity();
        pdf.setId(2L);
        pdf.setBook(book);
        pdf.setFileSubPath("author/title");
        pdf.setFileName("book.pdf");
        pdf.setBookFormat(true);

        BookFileEntity image = new BookFileEntity();
        image.setId(3L);
        image.setBook(book);
        image.setFileSubPath("author/title");
        image.setFileName("image.png");
        image.setBookFormat(false);

        book.setBookFiles(List.of(epub, pdf, image));
        libraryEntity.setBookEntities(List.of(book));

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(fileProcessorRegistry.getProcessor(libraryEntity)).thenReturn(libraryFileProcessor);
        when(libraryFileProcessor.supportsSupplementaryFiles()).thenReturn(false);

        LibraryFile epubOnDisk = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("author/title")
                .fileName("book.epub")
                .build();

        LibraryFile pdfOnDisk = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("author/title")
                .fileName("book.pdf")
                .build();

        when(libraryFileHelper.getLibraryFiles(libraryEntity, libraryFileProcessor)).thenReturn(List.of(epubOnDisk, pdfOnDisk));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(List.of(epub, pdf, image));

        libraryProcessingService.rescanLibrary(RescanLibraryContext.builder().libraryId(libraryId).build());

        verify(bookDeletionService, never()).deleteRemovedAdditionalFiles(any());
    }

    @Test
    void rescanLibrary_shouldAbortWhenPathNotAccessible(@TempDir Path tempDir) throws IOException {
        long libraryId = 1L;
        Path nonExistentPath = tempDir.resolve("non_existent_path");

        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setScanMode(LibraryScanMode.FILE_AS_BOOK);

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath(nonExistentPath.toString());
        libraryEntity.setLibraryPaths(List.of(pathEntity));
        libraryEntity.setBookEntities(Collections.emptyList());

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(fileProcessorRegistry.getProcessor(libraryEntity)).thenReturn(libraryFileProcessor);

        RescanLibraryContext context = RescanLibraryContext.builder().libraryId(libraryId).build();

        assertThatThrownBy(() -> libraryProcessingService.rescanLibrary(context))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("not accessible");

        verify(bookDeletionService, never()).processDeletedLibraryFiles(any(), any());
        verify(bookDeletionService, never()).deleteRemovedAdditionalFiles(any());
    }

    @Test
    void rescanLibrary_shouldAbortWhenLibraryHasBooksButScanReturnsEmpty(@TempDir Path tempDir) throws IOException {
        long libraryId = 1L;
        Path accessiblePath = tempDir.resolve("accessible");
        Files.createDirectory(accessiblePath);

        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setScanMode(LibraryScanMode.FILE_AS_BOOK);

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath(accessiblePath.toString());
        libraryEntity.setLibraryPaths(List.of(pathEntity));

        BookEntity existingBook = new BookEntity();
        existingBook.setId(1L);
        existingBook.setLibraryPath(pathEntity);
        BookFileEntity existingBookFile = new BookFileEntity();
        existingBookFile.setBook(existingBook);
        existingBook.setBookFiles(List.of(existingBookFile));
        existingBook.getPrimaryBookFile().setFileSubPath("");
        existingBook.getPrimaryBookFile().setFileName("book1.epub");
        libraryEntity.setBookEntities(List.of(existingBook));

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(fileProcessorRegistry.getProcessor(libraryEntity)).thenReturn(libraryFileProcessor);
        when(libraryFileHelper.getLibraryFiles(libraryEntity, libraryFileProcessor)).thenReturn(Collections.emptyList());

        RescanLibraryContext context = RescanLibraryContext.builder().libraryId(libraryId).build();

        assertThatThrownBy(() -> libraryProcessingService.rescanLibrary(context))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("not accessible");

        verify(bookDeletionService, never()).processDeletedLibraryFiles(any(), any());
        verify(bookDeletionService, never()).deleteRemovedAdditionalFiles(any());
    }

    @Test
    void rescanLibrary_shouldProceedWhenLibraryHasBooksAndScanFindsThem(@TempDir Path tempDir) throws IOException {
        long libraryId = 1L;
        Path accessiblePath = tempDir.resolve("accessible");
        Files.createDirectory(accessiblePath);

        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setScanMode(LibraryScanMode.FILE_AS_BOOK);

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath(accessiblePath.toString());
        libraryEntity.setLibraryPaths(List.of(pathEntity));

        BookEntity existingBook = new BookEntity();
        existingBook.setId(1L);
        existingBook.setLibraryPath(pathEntity);
        BookFileEntity existingBookFile = new BookFileEntity();
        existingBookFile.setBook(existingBook);
        existingBook.setBookFiles(List.of(existingBookFile));
        existingBook.getPrimaryBookFile().setFileSubPath("");
        existingBook.getPrimaryBookFile().setFileName("book1.epub");
        libraryEntity.setBookEntities(List.of(existingBook));

        LibraryFile fileOnDisk = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book1.epub")
                .build();

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(fileProcessorRegistry.getProcessor(libraryEntity)).thenReturn(libraryFileProcessor);
        when(libraryFileHelper.getLibraryFiles(libraryEntity, libraryFileProcessor)).thenReturn(List.of(fileOnDisk));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());

        RescanLibraryContext context = RescanLibraryContext.builder().libraryId(libraryId).build();

        libraryProcessingService.rescanLibrary(context);

        verify(bookDeletionService, never()).processDeletedLibraryFiles(any(), any());
    }

    @Test
    void rescanLibrary_shouldProceedForEmptyLibraryWithNoFilesFound(@TempDir Path tempDir) throws IOException {
        long libraryId = 1L;
        Path accessiblePath = tempDir.resolve("accessible");
        Files.createDirectory(accessiblePath);

        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setScanMode(LibraryScanMode.FILE_AS_BOOK);

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath(accessiblePath.toString());
        libraryEntity.setLibraryPaths(List.of(pathEntity));
        libraryEntity.setBookEntities(Collections.emptyList());

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(fileProcessorRegistry.getProcessor(libraryEntity)).thenReturn(libraryFileProcessor);
        when(libraryFileHelper.getLibraryFiles(libraryEntity, libraryFileProcessor)).thenReturn(Collections.emptyList());
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());

        RescanLibraryContext context = RescanLibraryContext.builder().libraryId(libraryId).build();

        libraryProcessingService.rescanLibrary(context);

        verify(bookDeletionService, never()).processDeletedLibraryFiles(any(), any());
    }
}
