package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.KoboSettings;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.ShelfType;
import com.adityachandel.booklore.service.ShelfService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.kobo.CbxConversionService;
import com.adityachandel.booklore.service.kobo.KepubConversionService;
import com.adityachandel.booklore.service.book.BookImportService;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("BookDownloadService (Kobo) Tests")
class BookDownloadServiceKoboTest {

    @TempDir
    Path tempDir;

    private BookRepository bookRepository;
    private KepubConversionService kepubConversionService;
    private CbxConversionService cbxConversionService;
    private AppSettingService appSettingService;
    private ShelfService shelfService;
    private AuthenticationService authenticationService;
    private LibraryRepository libraryRepository;
    private BookMetadataRepository bookMetadataRepository;
    private BookImportService bookImportService;
    private BookMapper bookMapper;

    private BookDownloadService bookDownloadService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bookRepository = mock(BookRepository.class);
        kepubConversionService = mock(KepubConversionService.class);
        cbxConversionService = mock(CbxConversionService.class);
        appSettingService = mock(AppSettingService.class);
        shelfService = mock(ShelfService.class);
        authenticationService = mock(AuthenticationService.class);
        libraryRepository = mock(LibraryRepository.class);
        bookMetadataRepository = mock(BookMetadataRepository.class);
        bookImportService = mock(BookImportService.class);
        bookMapper = mock(BookMapper.class);

        bookDownloadService = new BookDownloadService(
                bookRepository,
                kepubConversionService,
                cbxConversionService,
                appSettingService,
                shelfService,
                authenticationService,
                libraryRepository,
                bookMetadataRepository,
                bookImportService,
                bookMapper
        );
    }

    private BookEntity createBookEntity(Path libraryPathDir, String fileName, BookFileType type) {
        LibraryEntity lib = new LibraryEntity();
        lib.setId(11L);

        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setId(22L);
        libPath.setLibrary(lib);
        libPath.setPath(libraryPathDir.toString());

        BookEntity book = new BookEntity();
        book.setId(101L);
        book.setLibrary(lib);
        book.setLibraryPath(libPath);
        book.setFileSubPath("");
        book.setFileName(fileName);
        book.setBookType(type);
        book.setAddedOn(Instant.now());
        // ensure file size is small
        book.setFileSizeKb(1L);

        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setTitle("Test Title");
        book.setMetadata(meta);

        return book;
    }

    @Test
    void downloadKoboBook_persistConversion_true_callsImportAndUsesConversionShelf() throws Exception {
        // Arrange
        Path libraryDir = tempDir.resolve("library");
        Files.createDirectories(libraryDir);
        Path sourceFile = Files.createFile(libraryDir.resolve("comic.cbz"));
        // write minimal content to source file in case service checks file size
        Files.write(sourceFile, "source-content".getBytes());

        BookEntity book = createBookEntity(libraryDir, "comic.cbz", BookFileType.CBX);
        when(bookRepository.findById(101L)).thenReturn(Optional.of(book));

        KoboSettings kobo = KoboSettings.builder()
                .convertCbxToEpub(true)
                .persistConversion(true)
                .conversionLimitInMbForCbx(10)
                .build();
        com.adityachandel.booklore.model.dto.settings.AppSettings appSettings = new com.adityachandel.booklore.model.dto.settings.AppSettings();
        appSettings.setKoboSettings(kobo);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // converted file returned by conversion service lives in a temp dir
        Path converted = Files.createFile(tempDir.resolve("comic.cbz.epub"));
        // write some bytes into converted file so streaming produces non-empty response
        Files.write(converted, "converted-content".getBytes());
        when(cbxConversionService.convertCbxToEpub(eq(sourceFile.toFile()), any(File.class), eq(book))).thenReturn(converted.toFile());

        // authenticated user and conversion shelf (BookLoreUser DTO expected)
        BookLoreUser user = BookLoreUser.builder().id(55L).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        ShelfEntity shelf = new ShelfEntity();
        shelf.setId(77L);
        when(shelfService.getShelf(eq(user.getId()), eq(ShelfType.CONVERSION.getName()))).thenReturn(Optional.of(shelf));

        // map metadata
        com.adityachandel.booklore.model.dto.Book bookDto = com.adityachandel.booklore.model.dto.Book.builder()
                .metadata(new com.adityachandel.booklore.model.dto.BookMetadata())
                .build();
        when(bookMapper.toBook(book)).thenReturn(bookDto);

        // Mock import to return an imported Book DTO with id
        com.adityachandel.booklore.model.dto.Book importedDto = com.adityachandel.booklore.model.dto.Book.builder().id(999L).build();
        when(bookImportService.importFileToLibrary(any(File.class), anyLong(), anyLong(), any(), any())).thenReturn(importedDto);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        bookDownloadService.downloadKoboBook(101L, response);

        // Assert
        // verify import called and shelf lookup performed
        verify(bookImportService, atLeastOnce()).importFileToLibrary(any(File.class), eq(11L), eq(22L), any(), eq(77L));
        verify(shelfService, atLeastOnce()).getShelf(eq(user.getId()), eq(ShelfType.CONVERSION.getName()));

        // response should contain content-disposition
        assertThat(response.getHeader("Content-Disposition")).isNotNull();
        assertThat(response.getContentAsByteArray()).isNotEmpty();
    }

    @Test
    void downloadKoboBook_persistConversion_false_streamsWithoutImport() throws Exception {
        // Arrange
        Path libraryDir = tempDir.resolve("library2");
        Files.createDirectories(libraryDir);
        Path sourceFile = Files.createFile(libraryDir.resolve("comic2.cbz"));
        Files.write(sourceFile, "source-content-2".getBytes());

        BookEntity book = createBookEntity(libraryDir, "comic2.cbz", BookFileType.CBX);
        when(bookRepository.findById(101L)).thenReturn(Optional.of(book));

        KoboSettings kobo = KoboSettings.builder()
                .convertCbxToEpub(true)
                .persistConversion(false)
                .conversionLimitInMbForCbx(10)
                .build();
        com.adityachandel.booklore.model.dto.settings.AppSettings appSettings = new com.adityachandel.booklore.model.dto.settings.AppSettings();
        appSettings.setKoboSettings(kobo);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // converted file in tempDir (conversion output)
        Path converted = Files.createFile(tempDir.resolve("comic2.cbz.epub"));
        Files.write(converted, "converted-content-2".getBytes());
        when(cbxConversionService.convertCbxToEpub(eq(sourceFile.toFile()), any(File.class), eq(book))).thenReturn(converted.toFile());

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        bookDownloadService.downloadKoboBook(101L, response);

        // Assert
        verify(bookImportService, never()).importFileToLibrary(any(File.class), anyLong(), anyLong(), any(), any());
        verify(shelfService, never()).getShelf(anyLong(), eq(ShelfType.CONVERSION.getName()));

        assertThat(response.getHeader("Content-Disposition")).isNotNull();
        assertThat(response.getContentAsByteArray()).isNotEmpty();
    }
}
