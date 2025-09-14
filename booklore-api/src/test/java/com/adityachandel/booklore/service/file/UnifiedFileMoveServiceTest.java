package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnifiedFileMoveServiceTest {

    @Mock
    FileMovingHelper fileMovingHelper;

    @Mock
    MonitoredFileOperationService monitoredFileOperationService;

    @Mock
    MonitoringRegistrationService monitoringRegistrationService;

    @InjectMocks
    UnifiedFileMoveService service;

    @TempDir
    Path tmp;

    LibraryEntity library;
    LibraryPathEntity libraryPath;

    @BeforeEach
    void setup() {
        library = new LibraryEntity();
        library.setId(10L);
        library.setName("lib");

        libraryPath = new LibraryPathEntity();
        libraryPath.setId(20L);
        libraryPath.setPath(tmp.toString());
        libraryPath.setLibrary(library);

        library.setLibraryPaths(singletonList(libraryPath));
    }

    @Test
    void moveSingleBookFile_skipsWhenNoLibrary() {
        BookEntity book = new BookEntity();
        // no libraryPath set
        service.moveSingleBookFile(book);
        verifyNoInteractions(monitoredFileOperationService);
        verifyNoInteractions(fileMovingHelper);
    }

    @Test
    void moveSingleBookFile_skipsWhenFileMissing() throws Exception {
        BookEntity book = new BookEntity();
        book.setId(1L);
        book.setLibraryPath(libraryPath);
        book.setFileSubPath(".");
        book.setFileName("missing.pdf");

        when(fileMovingHelper.getFileNamingPattern(any())).thenReturn("{currentFilename}");

        service.moveSingleBookFile(book);

        verifyNoInteractions(monitoredFileOperationService);
    }

    @Test
    void moveSingleBookFile_executesMoveWhenNeeded() throws Exception {
        BookEntity book = new BookEntity();
        book.setId(2L);
        book.setLibraryPath(libraryPath);
        book.setFileSubPath(".");
        book.setFileName("book.pdf");

        Path src = tmp.resolve("book.pdf");
        Files.writeString(src, "data");

        Path expected = tmp.resolve("moved").resolve("book.pdf");

        when(fileMovingHelper.getFileNamingPattern(library)).thenReturn("{currentFilename}");
        when(fileMovingHelper.generateNewFilePath(eq(book), anyString())).thenReturn(expected);
        when(fileMovingHelper.hasRequiredPathComponents(eq(book))).thenReturn(true);

        doAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(3);
            supplier.get();
            return null;
        }).when(monitoredFileOperationService).executeWithMonitoringSuspended(any(), any(), anyLong(), any());

        when(fileMovingHelper.moveBookFileIfNeeded(eq(book), anyString())).thenAnswer(inv -> {
            book.setFileSubPath("moved");
            book.setFileName("book.pdf");
            return true;
        });

        Path actualSrc = book.getFullFilePath();

        service.moveSingleBookFile(book);

        verify(monitoredFileOperationService).executeWithMonitoringSuspended(eq(actualSrc), eq(expected), eq(10L), any());
        verify(fileMovingHelper).moveBookFileIfNeeded(eq(book), anyString());
        assertEquals("moved", book.getFileSubPath());
    }

    @Test
    void moveBatchBookFiles_movesBooks_and_callsCallback_and_reRegistersLibraries() throws Exception {
        BookEntity b1 = new BookEntity();
        b1.setId(11L);
        b1.setLibraryPath(libraryPath);
        b1.setFileSubPath(".");
        b1.setFileName("a.pdf");
        BookMetadataEntity m1 = new BookMetadataEntity();
        m1.setTitle("A");
        b1.setMetadata(m1);
        m1.setBook(b1);

        BookEntity b2 = new BookEntity();
        b2.setId(12L);
        b2.setLibraryPath(libraryPath);
        b2.setFileSubPath(".");
        b2.setFileName("b.pdf");
        BookMetadataEntity m2 = new BookMetadataEntity();
        m2.setTitle("B");
        b2.setMetadata(m2);
        m2.setBook(b2);

        Path p1 = tmp.resolve("a.pdf");
        Path p2 = tmp.resolve("b.pdf");
        Files.writeString(p1, "1");
        Files.writeString(p2, "2");

        when(fileMovingHelper.getFileNamingPattern(library)).thenReturn("{currentFilename}");
        when(fileMovingHelper.hasRequiredPathComponents(eq(b1))).thenReturn(true);
        when(fileMovingHelper.hasRequiredPathComponents(eq(b2))).thenReturn(true);

        when(fileMovingHelper.moveBookFileIfNeeded(eq(b1), anyString())).thenAnswer(inv -> {
            b1.setFileSubPath("moved");
            return true;
        });
        when(fileMovingHelper.moveBookFileIfNeeded(eq(b2), anyString())).thenReturn(false);

        BookAdditionalFileEntity add = new BookAdditionalFileEntity();
        add.setId(100L);
        add.setBook(b1);
        add.setFileSubPath(".");
        add.setFileName("extra.pdf");
        b1.setAdditionalFiles(List.of(add));
        doNothing().when(fileMovingHelper).moveAdditionalFiles(eq(b1), anyString());

        UnifiedFileMoveService.BatchMoveCallback cb = mock(UnifiedFileMoveService.BatchMoveCallback.class);

        service.moveBatchBookFiles(List.of(b1, b2), cb);

        verify(monitoringRegistrationService).unregisterLibrary(eq(10L));
        verify(fileMovingHelper).moveBookFileIfNeeded(eq(b1), anyString());
        verify(fileMovingHelper).moveBookFileIfNeeded(eq(b2), anyString());
        verify(cb).onBookMoved(eq(b1));
        verify(cb, never()).onBookMoved(eq(b2));
        verify(fileMovingHelper).moveAdditionalFiles(eq(b1), anyString());
        verify(monitoringRegistrationService).registerLibraryPaths(eq(10L), any());
    }

    @Test
    void moveBatchBookFiles_callsOnBookMoveFailed_onIOException() throws Exception {
        BookEntity b = new BookEntity();
        b.setId(21L);
        b.setLibraryPath(libraryPath);
        b.setFileSubPath(".");
        b.setFileName("c.pdf");
        BookMetadataEntity m = new BookMetadataEntity();
        m.setTitle("C");
        b.setMetadata(m);
        m.setBook(b);

        Path p = tmp.resolve("c.pdf");
        Files.writeString(p, "c");

        when(fileMovingHelper.getFileNamingPattern(library)).thenReturn("{currentFilename}");
        when(fileMovingHelper.hasRequiredPathComponents(eq(b))).thenReturn(true);
        when(fileMovingHelper.moveBookFileIfNeeded(eq(b), anyString())).thenThrow(new IOException("disk"));

        UnifiedFileMoveService.BatchMoveCallback cb = mock(UnifiedFileMoveService.BatchMoveCallback.class);

        service.moveBatchBookFiles(List.of(b), cb);

        verify(cb).onBookMoveFailed(eq(b), any(IOException.class));
        verify(monitoringRegistrationService).unregisterLibrary(eq(10L));
        verify(monitoringRegistrationService).registerLibraryPaths(eq(10L), any());
    }
}
