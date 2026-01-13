package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.LibraryMapper;
import com.adityachandel.booklore.model.dto.FileMoveResult;
import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileMoveServiceTest {

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

    private FileMoveService fileMoveService;

    private BookEntity bookEntity;
    private Path expectedFilePath;

    // Subclass to mock sleep for tests
    static class TestableFileMoveService extends FileMoveService {
        public TestableFileMoveService(BookRepository bookRepository, BookAdditionalFileRepository bookFileRepository, LibraryRepository libraryRepository, FileMoveHelper fileMoveHelper, MonitoringRegistrationService monitoringRegistrationService, LibraryMapper libraryMapper, BookMapper bookMapper, NotificationService notificationService, EntityManager entityManager) {
            super(bookRepository, bookFileRepository, libraryRepository, fileMoveHelper, monitoringRegistrationService, libraryMapper, bookMapper, notificationService, entityManager);
        }

        @Override
        protected void sleep(long millis) {
            // No-op for test
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Use spy/subclass to avoid actual sleep
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

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("SciFi");
        primaryFile.setFileName("Original.epub");
        bookEntity.setBookFiles(new ArrayList<>(List.of(primaryFile)));

        expectedFilePath = Paths.get(libraryPath.getPath(), bookEntity.getPrimaryBookFile().getFileSubPath(), "Renamed.epub");

        when(fileMoveHelper.getFileNamingPattern(library)).thenReturn("{title}");
        when(fileMoveHelper.generateNewFilePath(bookEntity, libraryPath, "{title}")).thenReturn(expectedFilePath);
        when(fileMoveHelper.extractSubPath(expectedFilePath, libraryPath)).thenReturn(bookEntity.getPrimaryBookFile().getFileSubPath());
        doNothing().when(fileMoveHelper).moveFile(any(Path.class), any(Path.class));
        doNothing().when(fileMoveHelper).deleteEmptyParentDirsUpToLibraryFolders(any(Path.class), anySet());
    }

    @Test
    void moveSingleFile_whenLibraryMonitored_reRegistersLibraryPaths() throws Exception {
        when(monitoringRegistrationService.isLibraryMonitored(42L)).thenReturn(true);
        when(monitoringRegistrationService.getPathsForLibraries(Set.of(42L))).thenReturn(Set.of(Paths.get("/library/root")));
        Library libraryDto = Library.builder().watch(true).build();
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(libraryDto);

        FileMoveResult result = fileMoveService.moveSingleFile(bookEntity);

        assertTrue(result.isMoved());

        verify(fileMoveHelper).unregisterLibrary(42L);
        verify(monitoringRegistrationService).registerLibrary(libraryDto);
        assertTrue(libraryDto.isWatch());
        verify(fileMoveHelper, never()).registerLibraryPaths(anyLong(), any(Path.class));
    }

    @Test
    void moveSingleFile_ensuresLibraryWatchStatusIsRestored() throws Exception {
        when(monitoringRegistrationService.isLibraryMonitored(42L)).thenReturn(true);
        when(monitoringRegistrationService.getPathsForLibraries(Set.of(42L))).thenReturn(Set.of(Paths.get("/library/root")));
        
        Library libraryDto = Library.builder().watch(false).build();
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(libraryDto);

        fileMoveService.moveSingleFile(bookEntity);

        verify(monitoringRegistrationService).registerLibrary(libraryDto);
        assertTrue(libraryDto.isWatch(), "Library watch status must be set to true before re-registering");
    }

    @Test
    void moveSingleFile_whenLibraryNotMonitored_skipsMonitoringCalls() throws Exception {
        when(monitoringRegistrationService.isLibraryMonitored(42L)).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(Set.of(42L))).thenReturn(Collections.emptySet());

        FileMoveResult result = fileMoveService.moveSingleFile(bookEntity);

        assertTrue(result.isMoved());

        verify(fileMoveHelper, never()).unregisterLibrary(anyLong());
        verify(fileMoveHelper, never()).registerLibraryPaths(anyLong(), any(Path.class));
    }

    @Test
    void bulkMoveFiles_movesAllBookFilesForBook() throws Exception {
        LibraryEntity targetLibrary = new LibraryEntity();
        targetLibrary.setId(43L);

        LibraryPathEntity targetLibraryPath = new LibraryPathEntity();
        targetLibraryPath.setId(88L);
        targetLibraryPath.setPath("/target/root");
        targetLibraryPath.setLibrary(targetLibrary);
        targetLibrary.setLibraryPaths(List.of(targetLibraryPath));

        BookFileEntity primary = bookEntity.getPrimaryBookFile();
        primary.setId(1L);

        BookFileEntity pdfAlt = new BookFileEntity();
        pdfAlt.setId(2L);
        pdfAlt.setBook(bookEntity);
        pdfAlt.setBookType(BookFileType.PDF);
        pdfAlt.setBookFormat(true);
        pdfAlt.setFileSubPath("SciFi");
        pdfAlt.setFileName("Original.pdf");

        BookFileEntity cover = new BookFileEntity();
        cover.setId(3L);
        cover.setBook(bookEntity);
        cover.setBookFormat(false);
        cover.setFileSubPath("SciFi");
        cover.setFileName("cover.png");

        bookEntity.setBookFiles(new ArrayList<>(List.of(primary, pdfAlt, cover)));
        pdfAlt.setBook(bookEntity);
        cover.setBook(bookEntity);

        Path sourcePrimary = Paths.get("/library/root", "SciFi", "Original.epub");
        Path sourcePdfAlt = Paths.get("/library/root", "SciFi", "Original.pdf");
        Path sourceCover = Paths.get("/library/root", "SciFi", "cover.png");

        Path targetPrimary = Paths.get("/target/root", "SciFi", "Renamed.epub");
        Path targetPdfAlt = Paths.get("/target/root", "SciFi", "Renamed.pdf");
        Path targetCover = Paths.get("/target/root", "SciFi", "cover.png");

        when(fileMoveHelper.getFileNamingPattern(targetLibrary)).thenReturn("{title}");
        when(fileMoveHelper.generateNewFilePath(bookEntity, targetLibraryPath, "{title}")).thenReturn(targetPrimary);
        when(fileMoveHelper.generateNewFilePath(bookEntity, primary, targetLibraryPath, "{title}")).thenReturn(targetPrimary);
        when(fileMoveHelper.generateNewFilePath(bookEntity, pdfAlt, targetLibraryPath, "{title}")).thenReturn(targetPdfAlt);
        when(fileMoveHelper.extractSubPath(targetPrimary, targetLibraryPath)).thenReturn("SciFi");

        when(bookRepository.findByIdWithBookFiles(bookEntity.getId())).thenReturn(Optional.of(bookEntity));

        when(fileMoveHelper.moveFileWithBackup(sourcePrimary)).thenReturn(sourcePrimary.resolveSibling("Original.epub.tmp_move"));
        when(fileMoveHelper.moveFileWithBackup(sourcePdfAlt)).thenReturn(sourcePdfAlt.resolveSibling("Original.pdf.tmp_move"));
        when(fileMoveHelper.moveFileWithBackup(sourceCover)).thenReturn(sourceCover.resolveSibling("cover.png.tmp_move"));

        doNothing().when(fileMoveHelper).commitMove(any(Path.class), any(Path.class));
        doNothing().when(entityManager).clear();
        when(bookMapper.toBookWithDescription(any(BookEntity.class), eq(false))).thenReturn(null);

        Set<Long> affected = new HashSet<>();
        affected.add(42L);
        affected.add(43L);

        when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Set.of(Paths.get("/library/root"), Paths.get("/target/root")));
        doNothing().when(monitoringRegistrationService).unregisterLibraries(anySet());
        when(monitoringRegistrationService.waitForEventsDrainedByPaths(anySet(), anyLong())).thenReturn(true);

        when(bookRepository.findById(anyLong())).thenReturn(Optional.of(bookEntity));
        when(libraryRepository.findById(anyLong())).thenReturn(Optional.of(targetLibrary));
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(null);
        doNothing().when(monitoringRegistrationService).registerLibrary(any());

        FileMoveRequest.Move m = new FileMoveRequest.Move();
        m.setBookId(bookEntity.getId());
        m.setTargetLibraryId(targetLibrary.getId());
        m.setTargetLibraryPathId(targetLibraryPath.getId());
        FileMoveRequest req = new FileMoveRequest();
        req.setMoves(List.of(m));

        fileMoveService.bulkMoveFiles(req);

        verify(fileMoveHelper).moveFileWithBackup(sourcePrimary);
        verify(fileMoveHelper).moveFileWithBackup(sourcePdfAlt);
        verify(fileMoveHelper).moveFileWithBackup(sourceCover);

        verify(fileMoveHelper).commitMove(any(Path.class), eq(targetPrimary));
        verify(fileMoveHelper).commitMove(any(Path.class), eq(targetPdfAlt));
        verify(fileMoveHelper).commitMove(any(Path.class), eq(targetCover));

        verify(bookFileRepository).updateFileNameAndSubPath(eq(primary.getId()), any(String.class), any(String.class));
        verify(bookFileRepository).updateFileNameAndSubPath(eq(pdfAlt.getId()), any(String.class), any(String.class));
        verify(bookFileRepository).updateFileNameAndSubPath(eq(cover.getId()), any(String.class), any(String.class));
        verify(bookRepository).updateLibrary(eq(bookEntity.getId()), eq(targetLibrary.getId()), eq(targetLibraryPath));
        verify(entityManager).clear();

        verify(notificationService).sendMessage(eq(Topic.BOOK_UPDATE), any());
    }

    @Test
    void moveSingleFile_whenLibraryNotMonitoredStatusButHasPaths_triggersUnregister() throws Exception {
        when(monitoringRegistrationService.isLibraryMonitored(42L)).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(Set.of(42L))).thenReturn(Set.of(Paths.get("/some/path")));
        Library libraryDto = Library.builder().watch(true).build();
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(libraryDto);

        FileMoveResult result = fileMoveService.moveSingleFile(bookEntity);

        assertTrue(result.isMoved());

        verify(fileMoveHelper).unregisterLibrary(42L);
        verify(monitoringRegistrationService).registerLibrary(libraryDto);
    }
}