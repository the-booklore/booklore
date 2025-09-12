package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoredFileOperationServiceTest {

    @Mock
    MonitoringRegistrationService monitoringRegistrationService;

    @InjectMocks
    MonitoredFileOperationService service;

    @TempDir
    Path tmp;

    Path sourceDir;
    Path targetDir;
    Path sourceFile;
    Path targetFile;
    final Long libraryId = 42L;

    @BeforeEach
    void init() throws IOException {
        sourceDir = tmp.resolve("source");
        Files.createDirectories(sourceDir);
        sourceFile = sourceDir.resolve("file.txt");
        Files.writeString(sourceFile, "data");

        targetDir = tmp.resolve("target");
        targetFile = targetDir.resolve("file.txt");
    }

    @Test
    void executeWithMonitoringSuspended_unregistersAndReregisters_whenDifferentPaths() {
        Supplier<String> operation = () -> {
            try {
                Files.createDirectories(targetDir);
                Files.createDirectories(targetDir.resolve("sub"));
                Files.writeString(targetFile, "moved");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return "ok";
        };

        when(monitoringRegistrationService.isPathMonitored(any()))
                .thenAnswer(invocation -> {
                    Path p = invocation.getArgument(0);
                    return p.equals(sourceDir);
                });

        service.executeWithMonitoringSuspended(sourceFile, targetFile, libraryId, operation);

        verify(monitoringRegistrationService).isPathMonitored(eq(sourceDir));
        verify(monitoringRegistrationService).unregisterSpecificPath(eq(sourceDir));
        verify(monitoringRegistrationService).registerSpecificPath(eq(sourceDir), eq(libraryId));
        verify(monitoringRegistrationService).registerSpecificPath(eq(targetDir), eq(libraryId));
        verify(monitoringRegistrationService).registerSpecificPath(eq(targetDir.resolve("sub")), eq(libraryId));
    }

    @Test
    void executeWithMonitoringSuspended_skipsDoubleUnregister_whenSamePaths() {
        Path src = sourceDir.resolve("a.txt");
        Path tgt = sourceDir.resolve("b.txt");

        when(monitoringRegistrationService.isPathMonitored(eq(sourceDir))).thenReturn(true);

        Supplier<Boolean> operation = () -> {
            try {
                Files.writeString(tgt, "x");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        };

        service.executeWithMonitoringSuspended(src, tgt, libraryId, operation);

        verify(monitoringRegistrationService).unregisterSpecificPath(eq(sourceDir));
        verify(monitoringRegistrationService, never()).unregisterSpecificPath(eq(targetDir));
        verify(monitoringRegistrationService).registerSpecificPath(eq(sourceDir), eq(libraryId));
    }

    @Test
    void executeWithMonitoringSuspended_reRegistersEvenIfOperationThrows() {
        when(monitoringRegistrationService.isPathMonitored(eq(sourceDir))).thenReturn(true);

        Supplier<Void> operation = () -> {
            throw new IllegalStateException("boom");
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.executeWithMonitoringSuspended(sourceFile, targetFile, libraryId, operation)
        );

        assertEquals("boom", ex.getMessage());
        verify(monitoringRegistrationService).unregisterSpecificPath(eq(sourceDir));
        verify(monitoringRegistrationService).registerSpecificPath(eq(sourceDir), eq(libraryId));
    }

    @Test
    void reregister_handlesFilesWalkIOException_gracefully() throws Exception {
        Supplier<String> operation = () -> {
            try {
                Files.createDirectories(targetDir);
                Files.writeString(targetFile, "moved");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return "ok";
        };

        when(monitoringRegistrationService.isPathMonitored(any()))
                .thenAnswer(invocation -> false);

        try (MockedStatic<Files> filesMock = mockStatic(Files.class, invocation -> invocation.callRealMethod())) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenCallRealMethod();
            filesMock.when(() -> Files.isDirectory(any(Path.class))).thenCallRealMethod();
            filesMock.when(() -> Files.walk(eq(targetDir))).thenThrow(new IOException("walk fail"));

            assertDoesNotThrow(() ->
                    service.executeWithMonitoringSuspended(sourceFile, targetFile, libraryId, operation)
            );

            verify(monitoringRegistrationService).registerSpecificPath(eq(targetDir), eq(libraryId));
        }
    }

    @Test
    void executeWithMonitoringSuspended_skipsReregister_whenSourceRemovedByOperation() {
        when(monitoringRegistrationService.isPathMonitored(eq(sourceDir))).thenReturn(true);

        Supplier<String> operation = () -> {
            try {
                Files.deleteIfExists(sourceFile);
                Files.deleteIfExists(sourceDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return "done";
        };

        service.executeWithMonitoringSuspended(sourceFile, targetFile, libraryId, operation);

        verify(monitoringRegistrationService).unregisterSpecificPath(eq(sourceDir));
        verify(monitoringRegistrationService, never()).registerSpecificPath(eq(sourceDir), eq(libraryId));
    }

    @Test
    void noUnregisters_whenNothingMonitored() {
        Supplier<String> operation = () -> {
            try {
                Files.createDirectories(targetDir);
                Files.writeString(targetFile, "ok");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return "ok";
        };

        when(monitoringRegistrationService.isPathMonitored(any())).thenReturn(false);

        service.executeWithMonitoringSuspended(sourceFile, targetFile, libraryId, operation);

        verify(monitoringRegistrationService, never()).unregisterSpecificPath(any());
        verify(monitoringRegistrationService).registerSpecificPath(eq(targetDir), eq(libraryId));
    }

    @Test
    void targetAlreadyMonitored_doesNotReRegister() throws Exception {
        Files.createDirectories(targetDir);
        when(monitoringRegistrationService.isPathMonitored(eq(sourceDir))).thenReturn(true);
        when(monitoringRegistrationService.isPathMonitored(eq(targetDir))).thenReturn(true);

        Supplier<String> operation = () -> {
            try {
                Files.writeString(targetFile, "x");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return "done";
        };

        service.executeWithMonitoringSuspended(sourceFile, targetFile, libraryId, operation);

        verify(monitoringRegistrationService).unregisterSpecificPath(eq(sourceDir));
        verify(monitoringRegistrationService).unregisterSpecificPath(eq(targetDir));
        verify(monitoringRegistrationService).registerSpecificPath(eq(targetDir), eq(libraryId));
    }
}
