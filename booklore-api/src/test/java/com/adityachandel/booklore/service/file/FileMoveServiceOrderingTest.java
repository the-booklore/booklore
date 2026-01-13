package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.LibraryMapper;
import com.adityachandel.booklore.model.dto.FileMoveResult;
import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileMoveServiceOrderingTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookAdditionalFileRepository bookFileRepository;
    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private FileMoveHelper fileMoveHelper;
    @Mock
    private MonitoringRegistrationService monitoringRegistrationService;
    @Mock
    private LibraryMapper libraryMapper;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EntityManager entityManager;

    private TestableFileMoveService fileMoveService;

    private BookEntity bookEntity;
    private Path expectedFilePath;

    // Subclass to mock sleep
    static class TestableFileMoveService extends FileMoveService {
        public TestableFileMoveService(BookRepository bookRepository, BookAdditionalFileRepository bookFileRepository, LibraryRepository libraryRepository, FileMoveHelper fileMoveHelper, MonitoringRegistrationService monitoringRegistrationService, LibraryMapper libraryMapper, BookMapper bookMapper, NotificationService notificationService, EntityManager entityManager) {
            super(bookRepository, bookFileRepository, libraryRepository, fileMoveHelper, monitoringRegistrationService, libraryMapper, bookMapper, notificationService, entityManager);
        }

        @Override
        protected void sleep(long millis) {
            // No-op for test, but can be verified by spy
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        fileMoveService = spy(new TestableFileMoveService(
                bookRepository, bookFileRepository, libraryRepository, fileMoveHelper, monitoringRegistrationService, libraryMapper, bookMapper, notificationService, entityManager));

        LibraryEntity library = new LibraryEntity();
        library.setId(42L);

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(77L);
        libraryPath.setPath("/library/root");
        libraryPath.setLibrary(library);

        bookEntity = new BookEntity();
        bookEntity.setId(999L);
        bookEntity.setLibrary(library);
        bookEntity.setLibraryPath(libraryPath);
        var primaryFile = new com.adityachandel.booklore.model.entity.BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setFileSubPath("SciFi");
        primaryFile.setFileName("Original.epub");
        bookEntity.setBookFiles(java.util.List.of(primaryFile));

        expectedFilePath = Paths.get(libraryPath.getPath(), primaryFile.getFileSubPath(), "Renamed.epub");

        when(fileMoveHelper.getFileNamingPattern(library)).thenReturn("{title}");
        when(fileMoveHelper.generateNewFilePath(bookEntity, libraryPath, "{title}")).thenReturn(expectedFilePath);
        when(fileMoveHelper.extractSubPath(expectedFilePath, libraryPath)).thenReturn(primaryFile.getFileSubPath());
        doNothing().when(fileMoveHelper).moveFile(any(Path.class), any(Path.class));
        doNothing().when(fileMoveHelper).deleteEmptyParentDirsUpToLibraryFolders(any(Path.class), anySet());
    }

    @Test
    void moveSingleFile_guaranteesCorrectOrderOfOperations_WhenMonitored() throws Exception {
        // Arrange
        when(monitoringRegistrationService.isLibraryMonitored(42L)).thenReturn(true);
        when(monitoringRegistrationService.getPathsForLibraries(Set.of(42L))).thenReturn(Set.of(Paths.get("/library/root")));
        
        Library libraryDto = Library.builder().watch(true).build();
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(libraryDto);

        // Act
        FileMoveResult result = fileMoveService.moveSingleFile(bookEntity);

        // Assert
        assertTrue(result.isMoved());

        InOrder inOrder = inOrder(monitoringRegistrationService, fileMoveHelper, fileMoveService);

        // 1. Unregister library
        inOrder.verify(fileMoveHelper).unregisterLibrary(42L);

        // 2. Wait for initial events to drain (before move)
        inOrder.verify(monitoringRegistrationService).waitForEventsDrainedByPaths(anySet(), anyLong());

        // 3. Perform the move
        inOrder.verify(fileMoveHelper).moveFile(any(Path.class), any(Path.class));

        // 4. Perform cleanup
        inOrder.verify(fileMoveHelper).deleteEmptyParentDirsUpToLibraryFolders(any(Path.class), anySet());

        // 5. CRITICAL: Sleep/Wait for events generated by move/cleanup to drain *before* re-registering
        inOrder.verify(fileMoveService).sleep(anyLong());

        // 6. Register library *last*
        inOrder.verify(monitoringRegistrationService).registerLibrary(libraryDto);
    }
    
    @Test
    void moveSingleFile_guaranteesCorrectOrderOfOperations_WhenStatusNotMonitoredButHasPaths() throws Exception {
        // Arrange: status says FALSE, but getPaths returns paths. Should still unregister.
        when(monitoringRegistrationService.isLibraryMonitored(42L)).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(Set.of(42L))).thenReturn(Set.of(Paths.get("/library/root")));
        
        Library libraryDto = Library.builder().watch(true).build();
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(libraryDto);

        // Act
        FileMoveResult result = fileMoveService.moveSingleFile(bookEntity);

        // Assert
        assertTrue(result.isMoved());

        InOrder inOrder = inOrder(monitoringRegistrationService, fileMoveHelper, fileMoveService);

        // 1. Unregister library
        inOrder.verify(fileMoveHelper).unregisterLibrary(42L);

        // 2. Wait for initial events to drain
        inOrder.verify(monitoringRegistrationService).waitForEventsDrainedByPaths(anySet(), anyLong());

        // 3. Move
        inOrder.verify(fileMoveHelper).moveFile(any(Path.class), any(Path.class));
        
        // 4. Sleep
        inOrder.verify(fileMoveService).sleep(anyLong());

        // 5. Register
        inOrder.verify(monitoringRegistrationService).registerLibrary(libraryDto);
    }

    @Test
    void moveSingleFile_skipsMonitoringOps_WhenNotMonitoredAndNoPaths() throws Exception {
        // Arrange
        when(monitoringRegistrationService.isLibraryMonitored(42L)).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(Set.of(42L))).thenReturn(Collections.emptySet());

        // Act
        FileMoveResult result = fileMoveService.moveSingleFile(bookEntity);

        // Assert
        assertTrue(result.isMoved());

        // Verify NO unregister/register happens
        verify(fileMoveHelper, never()).unregisterLibrary(anyLong());
        verify(monitoringRegistrationService, never()).waitForEventsDrainedByPaths(anySet(), anyLong());
        
        // Move happens
        verify(fileMoveHelper).moveFile(any(Path.class), any(Path.class));
        
        // NO sleep
        verify(fileMoveService, never()).sleep(anyLong());
        
        // NO register
        verify(monitoringRegistrationService, never()).registerLibrary(any());
    }
}