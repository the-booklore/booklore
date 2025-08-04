package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.util.FileUtils;
import com.adityachandel.booklore.util.builder.LibraryTestBuilder;
import static com.adityachandel.booklore.util.builder.LibraryTestBuilderAssert.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderAsBookFileProcessorExampleTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookAdditionalFileRepository bookAdditionalFileRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BookFileProcessorRegistry bookFileProcessorRegistry;

    @Mock
    private BookFileProcessor mockBookFileProcessor;

    @InjectMocks
    private FolderAsBookFileProcessor processor;

    @Captor
    private ArgumentCaptor<BookAdditionalFileEntity> additionalFileCaptor;

    private MockedStatic<FileUtils> fileUtilsMock;
    private  MockedStatic<FileFingerprint> fileFingerprintMock;
    private LibraryTestBuilder libraryTestBuilder;

    @BeforeEach
    void setUp() {
        fileUtilsMock = mockStatic(FileUtils.class);
        fileFingerprintMock = mockStatic(FileFingerprint.class);
        libraryTestBuilder = new LibraryTestBuilder(fileUtilsMock, fileFingerprintMock, bookFileProcessorRegistry, mockBookFileProcessor, bookRepository, bookAdditionalFileRepository);
    }

    @AfterEach
    void tearDown() {
        fileUtilsMock.close();
        fileFingerprintMock.close();
    }

    @Test
    void processLibraryFiles_shouldCreateNewBookFromDirectory() {
        // Given
        libraryTestBuilder
                .addDefaultLibrary()
                .addLibraryFile("/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile("/101/Anatomy", "Anatomy 101.pdf");

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101", "Anatomy 101")
                .hasNoAdditionalFiles();
    }

    @Test
    void processLibraryFiles_shouldCreateNewBookFromDirectoryWithAdditionalBookFormats() {
        // Given
        libraryTestBuilder
                .addDefaultLibrary()
                .addLibraryFile("/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile("/101/Accounting", "Accounting 101.epub")
                .addLibraryFile("/101/Anatomy", "Anatomy 101.pdf");

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101", "Anatomy 101")
                .bookHasAdditionalFormats("Accounting 101", BookFileType.PDF)
                .bookHasNoSupplementaryFiles("Accounting 101")
                .bookHasNoAdditionalFiles("Anatomy 101");
    }

    @Test
    void processLibraryFiles_shouldCreateNewBookFromDirectoryWithAdditionalBookFormatsAndSupplementaryFiles() {
        // Given
        libraryTestBuilder
                .addDefaultLibrary()
                .addLibraryFile("/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile("/101/Accounting", "Accounting 101.epub")
                .addLibraryFile("/101/Accounting", "Accounting 101.zip")
                .addLibraryFile("/101/Anatomy", "Anatomy 101.pdf");

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101", "Anatomy 101")
                .bookHasAdditionalFormats("Accounting 101", BookFileType.PDF)
                .bookHasSupplementaryFiles("Accounting 101", "Accounting 101.zip")
                .bookHasNoAdditionalFiles("Anatomy 101");
    }

    @Test
    void processLibraryFiles_shouldCreateNewBookFromDirectoryWithSameSupplementaryFilesByHash() {
        // Given
        String supplementaryFileHash = "hash-accounting-101";
        libraryTestBuilder
                .addDefaultLibrary()
                .addLibraryFile("/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile(
                        "/101/Accounting",
                        "Accounting 101.zip",
                        supplementaryFileHash)
                .addLibraryFile("/101/Accounting 2nd Edition", "Accounting 101 2nd Edition.pdf")
                .addLibraryFile(
                        "/101/Accounting 2nd Edition",
                        "Accounting 101 2nd Edition.zip",
                        supplementaryFileHash);

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101", "Accounting 101 2nd Edition")
                .bookHasSupplementaryFiles("Accounting 101", "Accounting 101.zip")
                .bookHasSupplementaryFiles("Accounting 101 2nd Edition", "Accounting 101 2nd Edition.zip");
    }

    @Test
    void processLibraryFiles_shouldIgnoreAdditionalFormatWithTheSameHashUsedInOtherBook() {
        // Given
        String additionalFormatHash = "hash-accounting-101";
        libraryTestBuilder
                .addDefaultLibrary()
                .addLibraryFile("/101/Accounting", "Accounting 101.epub")
                .addLibraryFile(
                        "/101/Accounting",
                        "Accounting 101.pdf",
                        additionalFormatHash)
                .addLibraryFile("/101/Accounting 2nd Edition", "Accounting 101 2nd Edition.epub")
                .addLibraryFile(
                        "/101/Accounting 2nd Edition",
                        "Accounting 101 2nd Edition.pdf",
                        additionalFormatHash);

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101", "Accounting 101 2nd Edition")
                .bookHasAdditionalFormats("Accounting 101", BookFileType.PDF)
                .bookHasNoAdditionalFiles("Accounting 101 2nd Edition");
    }
}
