package org.booklore.service.metadata.extractor;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.writer.PdfMetadataWriter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfMetadataExtractorTest {

    private PdfMetadataExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new PdfMetadataExtractor();
    }

    @Test
    void extractMetadata_shouldUseTitleFromMetadata_whenAvailable() throws IOException {
        // Arrange: Create a PDF with an explicit Title in metadata
        File pdfFile = tempDir.resolve("ignored-filename.pdf").toFile();

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("The Real Book Title");
            doc.setDocumentInformation(info);
            doc.save(pdfFile);
        }

        // Act
        BookMetadata result = extractor.extractMetadata(pdfFile);

        // Assert: Metadata title takes precedence over filename
        assertEquals("The Real Book Title", result.getTitle());
    }

    @Test
    void extractMetadata_shouldUseFilenameWithoutExtension_whenMetadataMissing() throws IOException {
        // Arrange: Create a PDF with NO metadata title
        File pdfFile = tempDir.resolve("Dune.pdf").toFile();

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(pdfFile);
        }

        // Act
        BookMetadata result = extractor.extractMetadata(pdfFile);

        // Assert: The extension ".pdf" should be stripped
        assertEquals("Dune", result.getTitle());
    }

    @Test
    void extractMetadata_shouldHandleSpacesAndSpecialCharsInFilename() throws IOException {
        // Arrange
        File pdfFile = tempDir.resolve("Harry Potter and the Sorcerer's Stone.pdf").toFile();

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(pdfFile);
        }

        // Act
        BookMetadata result = extractor.extractMetadata(pdfFile);

        // Assert
        assertEquals("Harry Potter and the Sorcerer's Stone", result.getTitle());
    }

    @Test
    void extractCover_validPdf_returnsJpegBytes() throws IOException {
        // Arrange: Create a simple one-page PDF
        File pdfFile = tempDir.resolve("cover-test.pdf").toFile();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            doc.save(pdfFile);
        }

        // Act
        byte[] coverBytes = extractor.extractCover(pdfFile);

        // Assert: Should return non-null, decodable JPEG bytes
        assertNotNull(coverBytes);
        assertTrue(coverBytes.length > 0);

        BufferedImage coverImage = ImageIO.read(new ByteArrayInputStream(coverBytes));
        assertNotNull(coverImage);
        assertTrue(coverImage.getWidth() > 0);
        assertTrue(coverImage.getHeight() > 0);
    }

    @Test
    @DisplayName("Round-trip: write purchaseDate to PDF then extract it back")
    void roundTrip_purchaseDate_writeAndReadBack() throws IOException {
        File pdfFile = tempDir.resolve("roundtrip-purchase.pdf").toFile();

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("Round Trip PDF");
            doc.setDocumentInformation(info);
            doc.save(pdfFile);
        }

        // Set up writer with mocked settings
        AppSettingService appSettingService = mock(AppSettingService.class);
        MetadataPersistenceSettings.FormatSettings pdfFormat = MetadataPersistenceSettings.FormatSettings.builder()
                .enabled(true).maxFileSizeInMb(100).build();
        MetadataPersistenceSettings.SaveToOriginalFile save = MetadataPersistenceSettings.SaveToOriginalFile.builder()
                .pdf(pdfFormat).build();
        MetadataPersistenceSettings persistence = new MetadataPersistenceSettings();
        persistence.setSaveToOriginalFile(save);
        AppSettings appSettings = mock(AppSettings.class);
        when(appSettings.getMetadataPersistenceSettings()).thenReturn(persistence);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        PdfMetadataWriter writer = new PdfMetadataWriter(appSettingService);

        Instant purchaseDate = Instant.parse("2024-03-20T14:00:00Z");
        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setTitle("Round Trip PDF");
        BookEntity bookEntity = new BookEntity();
        bookEntity.setPurchaseDate(purchaseDate);
        meta.setBook(bookEntity);

        writer.saveMetadataToFile(pdfFile, meta, null, new MetadataClearFlags());

        // Extract and verify round-trip
        BookMetadata extracted = extractor.extractMetadata(pdfFile);
        assertNotNull(extracted, "Extracted metadata should not be null");
        assertNotNull(extracted.getPurchaseDate(), "Extracted purchaseDate should not be null");
        assertEquals(purchaseDate, extracted.getPurchaseDate(),
                "Extracted purchaseDate should match written value");
    }

    @Test
    void extractCover_invalidPdf_returnsNull() throws IOException {
        // Arrange: Create a file that looks like a PDF but isn't
        File invalidPdf = tempDir.resolve("invalid-pdf.pdf").toFile();
        java.nio.file.Files.writeString(invalidPdf.toPath(), "this is not a real pdf");

        // Act
        byte[] coverBytes = extractor.extractCover(invalidPdf);

        // Assert: Should return null for invalid PDF
        assertNull(coverBytes);
    }
}
