package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.entity.BookEntity;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryProcessingServiceRegressionTest {

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
    void rescanLibrary_shouldThrowException_whenBookHasNoFiles(@TempDir Path tempDir) throws IOException {
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

        BookEntity bookWithNoFiles = new BookEntity();
        bookWithNoFiles.setId(1L);
        bookWithNoFiles.setLibraryPath(pathEntity);
        bookWithNoFiles.setBookFiles(Collections.emptyList()); // Empty files list
        
        libraryEntity.setBookEntities(List.of(bookWithNoFiles));

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(fileProcessorRegistry.getProcessor(libraryEntity)).thenReturn(libraryFileProcessor);
        // We need at least one file so it doesn't think the library is offline
        when(libraryFileHelper.getLibraryFiles(libraryEntity, libraryFileProcessor)).thenReturn(List.of(
            com.adityachandel.booklore.model.dto.settings.LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileName("other.epub")
                .fileSubPath("")
                .build()
        ));

        RescanLibraryContext context = RescanLibraryContext.builder().libraryId(libraryId).build();

        // Should not throw exception anymore
        libraryProcessingService.rescanLibrary(context);

        // Verify that the book with no files (ID 1) was detected as deleted
        verify(bookDeletionService).processDeletedLibraryFiles(
                argThat(list -> list.contains(1L)), 
                any()
        );
    }
}
