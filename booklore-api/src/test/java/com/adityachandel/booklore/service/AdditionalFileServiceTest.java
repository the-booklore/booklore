package com.adityachandel.booklore.service;

import com.adityachandel.booklore.mapper.AdditionalFileMapper;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.service.monitoring.MonitoringProtectionService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdditionalFileServiceTest {

    @Mock
    private BookAdditionalFileRepository additionalFileRepository;

    @Mock
    private AdditionalFileMapper additionalFileMapper;

    @Mock
    private MonitoringProtectionService monitoringProtectionService;

    @InjectMocks
    private AdditionalFileService additionalFileService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Configure mock to execute the Runnable for file operations (lenient for tests that don't use it)
        lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(monitoringProtectionService).executeWithProtection(any(Runnable.class), eq("additional file deletion"));
    }

    @Test
    void deleteAdditionalFile_success() throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("test-file.pdf");
        Files.createFile(testFile);

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());

        BookEntity book = new BookEntity();
        book.setLibraryPath(libraryPath);

        BookAdditionalFileEntity fileEntity = BookAdditionalFileEntity.builder()
                .id(1L)
                .fileName("test-file.pdf")
                .fileSubPath("")
                .additionalFileType(AdditionalFileType.ALTERNATIVE_FORMAT)
                .book(book)
                .build();

        when(additionalFileRepository.findById(1L)).thenReturn(Optional.of(fileEntity));

        // Act
        additionalFileService.deleteAdditionalFile(1L);

        // Assert
        verify(monitoringProtectionService).executeWithProtection(any(Runnable.class), eq("additional file deletion"));
        verify(additionalFileRepository).delete(fileEntity);
        assertThat(Files.exists(testFile)).isFalse();
    }

    @Test
    void deleteAdditionalFile_fileNotFound() {
        // Arrange
        when(additionalFileRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> additionalFileService.deleteAdditionalFile(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Additional file not found with id: 1");

        verify(monitoringProtectionService, never()).executeWithProtection(any(Runnable.class), any());
    }

    @Test
    void deleteAdditionalFile_physicalFileNotExists() {
        // Arrange
        Path nonExistentFile = tempDir.resolve("non-existent.pdf");

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());

        BookEntity book = new BookEntity();
        book.setLibraryPath(libraryPath);

        BookAdditionalFileEntity fileEntity = BookAdditionalFileEntity.builder()
                .id(1L)
                .fileName("non-existent.pdf")
                .fileSubPath("")
                .additionalFileType(AdditionalFileType.ALTERNATIVE_FORMAT)
                .book(book)
                .build();

        when(additionalFileRepository.findById(1L)).thenReturn(Optional.of(fileEntity));

        // Act
        additionalFileService.deleteAdditionalFile(1L);

        // Assert
        verify(monitoringProtectionService).executeWithProtection(any(Runnable.class), eq("additional file deletion"));
        verify(additionalFileRepository).delete(fileEntity);
    }

    @Test
    void deleteAdditionalFile_ioExceptionDuringDeletion() throws IOException {
        // This test verifies that DB record is still deleted even if file deletion fails
        // Arrange
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath("/invalid/path");

        BookEntity book = new BookEntity();
        book.setLibraryPath(libraryPath);

        BookAdditionalFileEntity fileEntity = BookAdditionalFileEntity.builder()
                .id(1L)
                .fileName("test-file.pdf")
                .fileSubPath("")
                .additionalFileType(AdditionalFileType.ALTERNATIVE_FORMAT)
                .book(book)
                .build();

        when(additionalFileRepository.findById(1L)).thenReturn(Optional.of(fileEntity));

        // Act
        additionalFileService.deleteAdditionalFile(1L);

        // Assert - DB record should still be deleted even if file deletion fails
        verify(monitoringProtectionService).executeWithProtection(any(Runnable.class), eq("additional file deletion"));
        verify(additionalFileRepository).delete(fileEntity);
    }

    @Test
    void deleteAdditionalFile_withMonitoringProtection() {
        // Arrange
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());

        BookEntity book = new BookEntity();
        book.setLibraryPath(libraryPath);

        BookAdditionalFileEntity fileEntity = BookAdditionalFileEntity.builder()
                .id(1L)
                .fileName("test-file.pdf")
                .fileSubPath("subfolder")
                .additionalFileType(AdditionalFileType.SUPPLEMENTARY)
                .book(book)
                .build();

        when(additionalFileRepository.findById(1L)).thenReturn(Optional.of(fileEntity));

        // Act
        additionalFileService.deleteAdditionalFile(1L);

        // Assert - Verify monitoring protection was used
        verify(monitoringProtectionService).executeWithProtection(any(Runnable.class), eq("additional file deletion"));
        
        // Verify the operation name is correct for logging/tracking
        verify(monitoringProtectionService).executeWithProtection(
            any(Runnable.class), 
            eq("additional file deletion")
        );
    }
}