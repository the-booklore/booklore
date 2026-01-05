package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        existingBook.setFileSubPath("");
        existingBook.setFileName("book1.epub");
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
        existingBook.setFileSubPath("");
        existingBook.setFileName("book1.epub");
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
        
        BookAdditionalFileEntity additionalFileEntity = new BookAdditionalFileEntity();
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
}
