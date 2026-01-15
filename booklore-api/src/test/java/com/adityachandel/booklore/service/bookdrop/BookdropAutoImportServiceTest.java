package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.response.BookdropFinalizeResult;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookdropAutoImportServiceTest {

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private BookDropService bookDropService;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private BookdropFileRepository bookdropFileRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private BookdropAutoImportService autoImportService;

    private BookdropFileEntity bookdropFileEntity;
    private LibraryEntity libraryEntity;
    private LibraryPathEntity libraryPathEntity;
    private AppSettings appSettings;
    private BookMetadata validMetadata;

    @BeforeEach
    void setUp() {
        // Setup library path
        libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath("/books");

        // Setup library
        libraryEntity = new LibraryEntity();
        libraryEntity.setId(1L);
        libraryEntity.setName("Test Library");
        libraryEntity.setLibraryPaths(new ArrayList<>(List.of(libraryPathEntity)));

        // Setup bookdrop file entity
        bookdropFileEntity = new BookdropFileEntity();
        bookdropFileEntity.setId(1L);
        bookdropFileEntity.setFileName("test-book.epub");
        bookdropFileEntity.setFilePath("/bookdrop/test-book.epub");
        bookdropFileEntity.setStatus(BookdropFileEntity.Status.PENDING_REVIEW);
        bookdropFileEntity.setCreatedAt(Instant.now());
        bookdropFileEntity.setUpdatedAt(Instant.now());

        // Setup valid metadata
        validMetadata = new BookMetadata();
        validMetadata.setTitle("Test Book Title");
        validMetadata.setAuthors(Set.of("Test Author"));

        // Setup app settings with auto-import disabled by default
        appSettings = AppSettings.builder()
                .autoImportEnabled(false)
                .metadataDownloadOnBookdrop(true)
                .build();
    }

    @Test
    void attemptAutoImport_WhenAutoImportDisabled_ShouldReturnFalse() {
        // Given
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertFalse(result);
        verify(bookDropService, never()).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenAutoImportEnabled_AndMetadataValid_ShouldAutoImport() throws Exception {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("{\"title\":\"Test Book\",\"authors\":[\"Test Author\"]}");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));

        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(validMetadata);
        when(libraryRepository.findAll()).thenReturn(List.of(libraryEntity));

        BookdropFinalizeResult finalizeResult = BookdropFinalizeResult.builder()
                .successfullyImported(1)
                .failed(0)
                .totalFiles(1)
                .processedAt(Instant.now())
                .build();
        when(bookDropService.finalizeImport(any())).thenReturn(finalizeResult);

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertTrue(result);
        verify(bookDropService).finalizeImport(any());
        verify(notificationService).sendMessageToPermissions(any(), any(), any());
    }

    @Test
    void attemptAutoImport_WhenMetadataHasNoTitle_ShouldReturnFalse() throws Exception {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("{\"authors\":[\"Test Author\"]}");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));

        BookMetadata metadataWithNoTitle = new BookMetadata();
        metadataWithNoTitle.setAuthors(Set.of("Test Author"));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(metadataWithNoTitle);

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertFalse(result);
        verify(bookDropService, never()).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenMetadataHasNoAuthors_ShouldReturnFalse() throws Exception {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("{\"title\":\"Test Book\"}");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));

        BookMetadata metadataWithNoAuthors = new BookMetadata();
        metadataWithNoAuthors.setTitle("Test Book");
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(metadataWithNoAuthors);

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertFalse(result);
        verify(bookDropService, never()).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenMetadataHasEmptyAuthors_ShouldReturnFalse() throws Exception {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("{\"title\":\"Test Book\",\"authors\":[]}");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));

        BookMetadata metadataWithEmptyAuthors = new BookMetadata();
        metadataWithEmptyAuthors.setTitle("Test Book");
        metadataWithEmptyAuthors.setAuthors(Set.of());
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(metadataWithEmptyAuthors);

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertFalse(result);
        verify(bookDropService, never()).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenNoFetchedMetadata_ShouldReturnFalse() {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata(null);
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertFalse(result);
        verify(bookDropService, never()).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenFetchedMetadataIsBlank_ShouldReturnFalse() {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("   ");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertFalse(result);
        verify(bookDropService, never()).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenNoLibraryAvailable_ShouldReturnFalse() throws Exception {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("{\"title\":\"Test Book\",\"authors\":[\"Test Author\"]}");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(validMetadata);

        when(libraryRepository.findAll()).thenReturn(List.of());

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertFalse(result);
        verify(bookDropService, never()).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenLibraryExists_ShouldSucceed() throws Exception {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("{\"title\":\"Test Book\",\"authors\":[\"Test Author\"]}");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(validMetadata);

        LibraryEntity availableLibrary = new LibraryEntity();
        availableLibrary.setId(2L);
        availableLibrary.setName("Available Library");
        availableLibrary.setLibraryPaths(new ArrayList<>(List.of(libraryPathEntity)));
        when(libraryRepository.findAll()).thenReturn(List.of(availableLibrary));

        BookdropFinalizeResult finalizeResult = BookdropFinalizeResult.builder()
                .successfullyImported(1)
                .failed(0)
                .totalFiles(1)
                .processedAt(Instant.now())
                .build();
        when(bookDropService.finalizeImport(any())).thenReturn(finalizeResult);

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertTrue(result);
        verify(bookDropService).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenLibraryHasNoPaths_ShouldReturnFalse() throws Exception {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("{\"title\":\"Test Book\",\"authors\":[\"Test Author\"]}");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(validMetadata);

        LibraryEntity libraryWithNoPaths = new LibraryEntity();
        libraryWithNoPaths.setId(3L);
        libraryWithNoPaths.setLibraryPaths(new ArrayList<>());
        when(libraryRepository.findAll()).thenReturn(List.of(libraryWithNoPaths));

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertFalse(result);
        verify(bookDropService, never()).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenBookdropFileNotFound_ShouldReturnFalse() {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(bookdropFileRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        boolean result = autoImportService.attemptAutoImport(999L);

        // Then
        assertFalse(result);
        verify(bookDropService, never()).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenFinalizeImportThrowsException_ShouldReturnFalse() throws Exception {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("{\"title\":\"Test Book\",\"authors\":[\"Test Author\"]}");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(validMetadata);
        when(libraryRepository.findAll()).thenReturn(List.of(libraryEntity));

        when(bookDropService.finalizeImport(any())).thenThrow(new RuntimeException("Import failed"));

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertFalse(result);
        verify(bookDropService).finalizeImport(any());
        // Should send error notification
        verify(notificationService).sendMessageToPermissions(any(), any(), any());
    }

    @Test
    void attemptAutoImport_WhenMetadataParsingFails_ShouldReturnFalse() throws Exception {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("invalid json");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class)))
                .thenThrow(new RuntimeException("JSON parsing error"));

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertFalse(result);
        verify(bookDropService, never()).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenTitleIsBlank_ShouldReturnFalse() throws Exception {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("{\"title\":\"   \",\"authors\":[\"Test Author\"]}");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));

        BookMetadata metadataWithBlankTitle = new BookMetadata();
        metadataWithBlankTitle.setTitle("   ");
        metadataWithBlankTitle.setAuthors(Set.of("Test Author"));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(metadataWithBlankTitle);

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertFalse(result);
        verify(bookDropService, never()).finalizeImport(any());
    }

    @Test
    void attemptAutoImport_WhenMultipleLibrariesExist_ShouldUseFirstLibrary() throws Exception {
        // Given
        appSettings = AppSettings.builder().autoImportEnabled(true).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        bookdropFileEntity.setFetchedMetadata("{\"title\":\"Test Book\",\"authors\":[\"Test Author\"]}");
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(bookdropFileEntity));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(validMetadata);

        // Create distinct path entities for each library
        LibraryPathEntity firstPathEntity = new LibraryPathEntity();
        firstPathEntity.setId(10L);
        firstPathEntity.setPath("/first-books");

        LibraryPathEntity secondPathEntity = new LibraryPathEntity();
        secondPathEntity.setId(20L);
        secondPathEntity.setPath("/second-books");

        // Create multiple libraries with their own paths
        List<LibraryPathEntity> firstPaths = new ArrayList<>();
        firstPaths.add(firstPathEntity);

        List<LibraryPathEntity> secondPaths = new ArrayList<>();
        secondPaths.add(secondPathEntity);

        LibraryEntity firstLibrary = LibraryEntity.builder()
                .id(2L)
                .name("First Library")
                .libraryPaths(firstPaths)
                .build();

        LibraryEntity secondLibrary = LibraryEntity.builder()
                .id(3L)
                .name("Second Library")
                .libraryPaths(secondPaths)
                .build();

        when(libraryRepository.findAll()).thenReturn(List.of(firstLibrary, secondLibrary));

        BookdropFinalizeResult finalizeResult = BookdropFinalizeResult.builder()
                .successfullyImported(1)
                .failed(0)
                .totalFiles(1)
                .processedAt(Instant.now())
                .build();
        when(bookDropService.finalizeImport(any())).thenReturn(finalizeResult);

        // When
        boolean result = autoImportService.attemptAutoImport(1L);

        // Then
        assertTrue(result);
        // Should use the first library in the list
        verify(bookDropService).finalizeImport(argThat(request ->
            request.getDefaultLibraryId().equals(2L)
        ));
    }
}
