package org.booklore.service.library;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.LibraryMapper;
import org.booklore.model.dto.LibraryPath;
import org.booklore.model.dto.request.CreateLibraryRequest;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryPathRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.monitoring.MonitoringService;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LibraryServiceScanTimeoutTest {

    @Mock private LibraryRepository libraryRepository;
    @Mock private LibraryPathRepository libraryPathRepository;
    @Mock private BookRepository bookRepository;
    @Mock private LibraryProcessingService libraryProcessingService;
    @Mock private BookMapper bookMapper;
    @Mock private LibraryMapper libraryMapper;
    @Mock private NotificationService notificationService;
    @Mock private FileService fileService;
    @Mock private MonitoringService monitoringService;
    @Mock private AuthenticationService authenticationService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private LibraryService libraryService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // no-op, @InjectMocks handles setup
    }

    @Test
    void scanLibraryPaths_withNullPaths_returnsZero() {
        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(null)
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPaths(request);
        assertEquals(0, result);
    }

    @Test
    void scanLibraryPaths_withEmptyPaths_returnsZero() {
        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(Collections.emptyList())
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPaths(request);
        assertEquals(0, result);
    }

    @Test
    void scanLibraryPaths_withNonExistentPath_returnsZero() {
        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(LibraryPath.builder().path("/nonexistent/path/abc123").build()))
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPaths(request);
        assertEquals(0, result);
    }

    @Test
    void scanLibraryPaths_countsProcessableFiles() throws IOException {
        Files.createFile(tempDir.resolve("book1.epub"));
        Files.createFile(tempDir.resolve("book2.pdf"));
        Files.createFile(tempDir.resolve("book3.mobi"));
        Files.createFile(tempDir.resolve("readme.txt")); // not processable

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(LibraryPath.builder().path(tempDir.toString()).build()))
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPaths(request);
        assertEquals(3, result);
    }

    @Test
    void scanLibraryPaths_respectsAllowedFormats() throws IOException {
        Files.createFile(tempDir.resolve("book1.epub"));
        Files.createFile(tempDir.resolve("book2.pdf"));
        Files.createFile(tempDir.resolve("book3.mobi"));

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(LibraryPath.builder().path(tempDir.toString()).build()))
                .allowedFormats(List.of(BookFileType.EPUB))
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPaths(request);
        assertEquals(1, result);
    }

    @Test
    void scanLibraryPaths_countsFilesInSubdirectories() throws IOException {
        Path subDir = Files.createDirectory(tempDir.resolve("subdir"));
        Path nestedDir = Files.createDirectory(subDir.resolve("nested"));

        Files.createFile(tempDir.resolve("book1.epub"));
        Files.createFile(subDir.resolve("book2.pdf"));
        Files.createFile(nestedDir.resolve("book3.cbz"));

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(LibraryPath.builder().path(tempDir.toString()).build()))
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPaths(request);
        assertEquals(3, result);
    }

    @Test
    void scanLibraryPaths_withMultiplePaths() throws IOException {
        Path dir1 = Files.createDirectory(tempDir.resolve("lib1"));
        Path dir2 = Files.createDirectory(tempDir.resolve("lib2"));

        Files.createFile(dir1.resolve("book1.epub"));
        Files.createFile(dir2.resolve("book2.pdf"));
        Files.createFile(dir2.resolve("book3.pdf"));

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(
                        LibraryPath.builder().path(dir1.toString()).build(),
                        LibraryPath.builder().path(dir2.toString()).build()
                ))
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPaths(request);
        assertEquals(3, result);
    }

    @Test
    void scanLibraryPathsWithTimeout_completesWithinTimeout() throws Exception {
        Files.createFile(tempDir.resolve("book1.epub"));
        Files.createFile(tempDir.resolve("book2.pdf"));

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(LibraryPath.builder().path(tempDir.toString()).build()))
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPathsWithTimeout(request, 10);
        assertEquals(2, result);
    }

    @Test
    void scanLibraryPathsWithTimeout_throwsTimeoutExceptionWhenSlow() {
        // Use a spy to simulate a slow scan
        LibraryService spyService = new LibraryService(
                libraryRepository, libraryPathRepository, bookRepository,
                libraryProcessingService, bookMapper, libraryMapper,
                notificationService, fileService, monitoringService,
                authenticationService, userRepository
        ) {
            @Override
            int countProcessableFiles(CreateLibraryRequest request) {
                try {
                    // Simulate a very slow file system
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return 9999;
            }
        };

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(LibraryPath.builder().path(tempDir.toString()).build()))
                .watch(false)
                .build();

        assertThrows(TimeoutException.class, () ->
                spyService.scanLibraryPathsWithTimeout(request, 1)
        );
    }

    @Test
    void scanLibraryPaths_returnsMinusOneOnTimeout() {
        // Use a subclass to simulate a slow scan that triggers the timeout path
        LibraryService slowService = new LibraryService(
                libraryRepository, libraryPathRepository, bookRepository,
                libraryProcessingService, bookMapper, libraryMapper,
                notificationService, fileService, monitoringService,
                authenticationService, userRepository
        ) {
            @Override
            int scanLibraryPathsWithTimeout(CreateLibraryRequest request, int timeoutSeconds) throws TimeoutException {
                throw new TimeoutException("Simulated timeout");
            }
        };

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(LibraryPath.builder().path(tempDir.toString()).build()))
                .watch(false)
                .build();

        int result = slowService.scanLibraryPaths(request);
        assertEquals(LibraryService.SCAN_TIMEOUT_RESULT, result);
        assertEquals(-1, result);
    }

    @Test
    void countProcessableFiles_handlesInterruptionGracefully() throws Exception {
        // Create many files
        for (int i = 0; i < 50; i++) {
            Files.createFile(tempDir.resolve("book" + i + ".epub"));
        }

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(LibraryPath.builder().path(tempDir.toString()).build()))
                .watch(false)
                .build();

        // Interrupt current thread before calling countProcessableFiles
        Thread.currentThread().interrupt();
        int result = libraryService.countProcessableFiles(request);

        // Should return 0 or partial count since thread was interrupted before scanning
        assertTrue(result >= 0, "Result should be non-negative even when interrupted");
        // Clear the interrupt flag
        assertTrue(Thread.interrupted(), "Thread should still have interrupt flag set or cleared");
    }

    @Test
    void scanLibraryPaths_countsSingleFile() throws IOException {
        Path singleFile = Files.createFile(tempDir.resolve("single.pdf"));

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(LibraryPath.builder().path(singleFile.toString()).build()))
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPaths(request);
        assertEquals(1, result);
    }

    @Test
    void scanLibraryPaths_singleNonProcessableFile_returnsZero() throws IOException {
        Path singleFile = Files.createFile(tempDir.resolve("readme.txt"));

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(LibraryPath.builder().path(singleFile.toString()).build()))
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPaths(request);
        assertEquals(0, result);
    }

    @Test
    void scanTimeoutConstant_hasExpectedValue() {
        assertEquals(30, LibraryService.SCAN_TIMEOUT_SECONDS);
    }

    @Test
    void scanTimeoutResult_isMinusOne() {
        assertEquals(-1, LibraryService.SCAN_TIMEOUT_RESULT);
    }

    @Test
    void scanLibraryPaths_countsAllSupportedFormats() throws IOException {
        Files.createFile(tempDir.resolve("book.epub"));
        Files.createFile(tempDir.resolve("book.pdf"));
        Files.createFile(tempDir.resolve("book.cbz"));
        Files.createFile(tempDir.resolve("book.cbr"));
        Files.createFile(tempDir.resolve("book.cb7"));
        Files.createFile(tempDir.resolve("book.fb2"));
        Files.createFile(tempDir.resolve("book.mobi"));
        Files.createFile(tempDir.resolve("book.azw3"));
        Files.createFile(tempDir.resolve("book.m4b"));

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(LibraryPath.builder().path(tempDir.toString()).build()))
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPaths(request);
        assertEquals(9, result);
    }

    @Test
    void scanLibraryPaths_mixedExistentAndNonExistentPaths() throws IOException {
        Files.createFile(tempDir.resolve("book.epub"));

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Test")
                .paths(List.of(
                        LibraryPath.builder().path(tempDir.toString()).build(),
                        LibraryPath.builder().path("/nonexistent/path/xyz").build()
                ))
                .watch(false)
                .build();

        int result = libraryService.scanLibraryPaths(request);
        assertEquals(1, result);
    }
}
