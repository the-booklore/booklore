package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.LibraryMapper;
import com.adityachandel.booklore.model.dto.FileMoveResult;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileMoveServiceTest {

    @Mock
    private BookRepository bookRepository;
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

    @InjectMocks
    private FileMoveService fileMoveService;

    private BookEntity bookEntity;
    private Path expectedFilePath;

    @BeforeEach
    void setUp() throws Exception {
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
        bookEntity.setFileSubPath("SciFi");
        bookEntity.setFileName("Original.epub");

        expectedFilePath = Paths.get(libraryPath.getPath(), bookEntity.getFileSubPath(), "Renamed.epub");

        when(fileMoveHelper.getFileNamingPattern(library)).thenReturn("{title}");
        when(fileMoveHelper.generateNewFilePath(bookEntity, libraryPath, "{title}")).thenReturn(expectedFilePath);
        when(fileMoveHelper.extractSubPath(expectedFilePath, libraryPath)).thenReturn(bookEntity.getFileSubPath());
        doNothing().when(fileMoveHelper).moveFile(any(Path.class), any(Path.class));
        doNothing().when(fileMoveHelper).deleteEmptyParentDirsUpToLibraryFolders(any(Path.class), anySet());
    }

    @Test
    void moveSingleFile_whenLibraryMonitored_reRegistersLibraryPaths() {
        when(monitoringRegistrationService.isLibraryMonitored(42L)).thenReturn(true);

        FileMoveResult result = fileMoveService.moveSingleFile(bookEntity);

        assertTrue(result.isMoved());

        verify(fileMoveHelper).unregisterLibrary(42L);
        Path expectedRoot = Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize();
        verify(fileMoveHelper).registerLibraryPaths(42L, expectedRoot);
    }

    @Test
    void moveSingleFile_whenLibraryNotMonitored_skipsMonitoringCalls() {
        when(monitoringRegistrationService.isLibraryMonitored(42L)).thenReturn(false);

        FileMoveResult result = fileMoveService.moveSingleFile(bookEntity);

        assertTrue(result.isMoved());

        verify(fileMoveHelper, never()).unregisterLibrary(anyLong());
        verify(fileMoveHelper, never()).registerLibraryPaths(anyLong(), any(Path.class));
    }
}
