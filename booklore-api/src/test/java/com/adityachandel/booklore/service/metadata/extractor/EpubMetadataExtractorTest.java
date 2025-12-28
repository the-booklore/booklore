package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class EpubMetadataExtractorTest {

    private static final String DEFAULT_TITLE = "Test Book";
    private static final String DEFAULT_AUTHOR = "John Doe";
    private static final String DEFAULT_PUBLISHER = "Test Publisher";
    private static final String DEFAULT_LANGUAGE = "en";

    private EpubMetadataExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new EpubMetadataExtractor();
    }

    @Nested
    @DisplayName("Date Parsing Tests")
    class DateParsingTests {

        @Test
        @DisplayName("Should parse year-only date format (e.g., '2024') as January 1st of that year")
        void parseDate_yearOnly_returnsJanuary1st() throws IOException {
            File epubFile = createEpubWithDate("2024");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(LocalDate.of(2024, 1, 1), result.getPublishedDate());
        }

        @Test
        @DisplayName("Should parse year 1972 correctly")
        void parseDate_year1972_returnsJanuary1st() throws IOException {
            File epubFile = createEpubWithDate("1972");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(LocalDate.of(1972, 1, 1), result.getPublishedDate());
        }

        @ParameterizedTest
        @DisplayName("Should parse various year-only formats correctly")
        @CsvSource({
            "1999, 1999-01-01",
            "2000, 2000-01-01",
            "2010, 2010-01-01",
            "2023, 2023-01-01",
            "2024, 2024-01-01",
            "1850, 1850-01-01",
            "1001, 1001-01-01",
            "9999, 9999-01-01"
        })
        void parseDate_variousYears_returnsCorrectDate(String year, String expectedDate) throws IOException {
            File epubFile = createEpubWithDate(year);

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(LocalDate.parse(expectedDate), result.getPublishedDate());
        }

        @ParameterizedTest
        @DisplayName("Should handle whitespace in year-only dates")
        @CsvSource({
            "' 2024 ', 2024-01-01",
            "'\t2024', 2024-01-01",
            "'  2024  ', 2024-01-01"
        })
        void parseDate_yearWithWhitespace_trimsAndParses(String dateWithSpace, String expectedDate) throws IOException {
            File epubFile = createEpubWithDate(dateWithSpace);

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(LocalDate.parse(expectedDate), result.getPublishedDate());
        }

        @Test
        @DisplayName("Should reject invalid year 0000")
        void parseDate_yearZero_returnsNull() throws IOException {
            File epubFile = createEpubWithDate("0000");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertNull(result.getPublishedDate());
        }

        @Test
        @DisplayName("Should reject year greater than 9999")
        void parseDate_yearTooLarge_returnsNull() throws IOException {
            File epubFile = createEpubWithDate("10000");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertNull(result.getPublishedDate());
        }

        @Test
        @DisplayName("Should parse full ISO date format (yyyy-MM-dd)")
        void parseDate_fullIsoDate_returnsCorrectDate() throws IOException {
            File epubFile = createEpubWithDate("2024-06-15");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(LocalDate.of(2024, 6, 15), result.getPublishedDate());
        }

        @Test
        @DisplayName("Should parse ISO datetime with timezone offset")
        void parseDate_isoDateTimeWithOffset_returnsCorrectDate() throws IOException {
            File epubFile = createEpubWithDate("2024-06-15T10:30:00+02:00");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(LocalDate.of(2024, 6, 15), result.getPublishedDate());
        }

        @Test
        @DisplayName("Should parse ISO datetime with Z timezone")
        void parseDate_isoDateTimeWithZ_returnsCorrectDate() throws IOException {
            File epubFile = createEpubWithDate("2024-06-15T10:30:00Z");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(LocalDate.of(2024, 6, 15), result.getPublishedDate());
        }

        @Test
        @DisplayName("Should parse date with extra content after first 10 characters")
        void parseDate_dateWithExtraContent_returnsCorrectDate() throws IOException {
            File epubFile = createEpubWithDate("2024-06-15T00:00:00");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(LocalDate.of(2024, 6, 15), result.getPublishedDate());
        }

        @ParameterizedTest
        @DisplayName("Should return null for invalid date formats")
        @ValueSource(strings = {"invalid", "20", "202", "abc1234", "2024/06/15"})
        void parseDate_invalidFormats_returnsNullDate(String invalidDate) throws IOException {
            File epubFile = createEpubWithDate(invalidDate);

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertNull(result.getPublishedDate());
        }

        @Test
        @DisplayName("Should handle whitespace in full date format")
        void parseDate_fullDateWithWhitespace_trimsAndParses() throws IOException {
            File epubFile = createEpubWithDate("  2024-06-15  ");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(LocalDate.of(2024, 6, 15), result.getPublishedDate());
        }
    }

    @Nested
    @DisplayName("Metadata Extraction Tests")
    class MetadataExtractionTests {

        @Test
        @DisplayName("Should extract title from EPUB metadata")
        void extractMetadata_withTitle_returnsTitle() throws IOException {
            File epubFile = createEpubWithMetadata(DEFAULT_TITLE, null, null, null);

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(DEFAULT_TITLE, result.getTitle());
        }

        @Test
        @DisplayName("Should extract author from EPUB metadata")
        void extractMetadata_withAuthor_returnsAuthor() throws IOException {
            File epubFile = createEpubWithMetadata(DEFAULT_TITLE, DEFAULT_AUTHOR, null, null);

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertTrue(result.getAuthors().contains(DEFAULT_AUTHOR));
        }

        @Test
        @DisplayName("Should extract multiple authors from EPUB metadata")
        void extractMetadata_withMultipleAuthors_returnsAllAuthors() throws IOException {
            File epubFile = createEpubWithMultipleAuthors(DEFAULT_TITLE, DEFAULT_AUTHOR, "Jane Smith");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertTrue(result.getAuthors().contains(DEFAULT_AUTHOR));
            assertTrue(result.getAuthors().contains("Jane Smith"));
            assertEquals(2, result.getAuthors().size());
        }

        @Test
        @DisplayName("Should not extract non-authors from EPUB metadata")
        void extractMetadata_withExtraCreators_returnsOnlyAuthors() throws IOException {
            File epubFile = createEpubWithExtraCreators(DEFAULT_TITLE, DEFAULT_AUTHOR, "Jane Smith", "Alice", "Bob");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertTrue(result.getAuthors().contains(DEFAULT_AUTHOR));
            assertTrue(result.getAuthors().contains("Jane Smith"));
            assertFalse(result.getAuthors().contains("Alice"));
            assertFalse(result.getAuthors().contains("Bob"));
            assertEquals(2, result.getAuthors().size());
        }

        @Test
        @DisplayName("Should extract publisher from EPUB metadata")
        void extractMetadata_withPublisher_returnsPublisher() throws IOException {
            File epubFile = createEpubWithMetadata(DEFAULT_TITLE, null, DEFAULT_PUBLISHER, null);

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(DEFAULT_PUBLISHER, result.getPublisher());
        }

        @Test
        @DisplayName("Should extract language from EPUB metadata")
        void extractMetadata_withLanguage_returnsLanguage() throws IOException {
            File epubFile = createEpubWithMetadata(DEFAULT_TITLE, null, null, DEFAULT_LANGUAGE);

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals(DEFAULT_LANGUAGE, result.getLanguage());
        }

        @Test
        @DisplayName("Should extract all metadata fields when provided")
        void extractMetadata_withAllFields_returnsCompleteMetadata() throws IOException {
            File epubFile = createEpubWithMetadata(DEFAULT_TITLE, DEFAULT_AUTHOR, DEFAULT_PUBLISHER, DEFAULT_LANGUAGE);

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(DEFAULT_TITLE, result.getTitle()),
                () -> assertTrue(result.getAuthors().contains(DEFAULT_AUTHOR)),
                () -> assertEquals(DEFAULT_PUBLISHER, result.getPublisher()),
                () -> assertEquals(DEFAULT_LANGUAGE, result.getLanguage())
            );
        }

        @Test
        @DisplayName("Should use filename as title when title is missing")
        void extractMetadata_withoutTitle_usesFilename() throws IOException {
            File epubFile = createEpubWithMetadata(null, null, null, null);
            Path renamedPath = tempDir.resolve("My Book Name.epub");
            Files.move(epubFile.toPath(), renamedPath, StandardCopyOption.REPLACE_EXISTING);
            File renamedFile = renamedPath.toFile();

            BookMetadata result = extractor.extractMetadata(renamedFile);

            assertNotNull(result);
            assertEquals("My Book Name", result.getTitle());
        }
    }

    @Nested
    @DisplayName("Series Metadata Tests")
    class SeriesMetadataTests {

        @Test
        @DisplayName("Should extract Calibre series metadata")
        void extractMetadata_withCalibreSeries_returnsSeriesInfo() throws IOException {
            File epubFile = createEpubWithCalibreSeries(DEFAULT_TITLE, "The Great Series", "3");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertAll(
                () -> assertNotNull(result),
                () -> assertEquals("The Great Series", result.getSeriesName()),
                () -> assertEquals(3.0f, result.getSeriesNumber(), 0.001)
            );
        }

        @Test
        @DisplayName("Should extract booklore series metadata")
        void extractMetadata_withBookloreSeries_returnsSeriesInfo() throws IOException {
            File epubFile = createEpubWithBookloreSeries(DEFAULT_TITLE, "My Series", "2.5");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertAll(
                () -> assertNotNull(result),
                () -> assertEquals("My Series", result.getSeriesName()),
                () -> assertEquals(2.5f, result.getSeriesNumber(), 0.001)
            );
        }

        @Test
        @DisplayName("Should handle invalid series index gracefully")
        void extractMetadata_withInvalidSeriesIndex_handlesGracefully() throws IOException {
            File epubFile = createEpubWithCalibreSeries(DEFAULT_TITLE, "Series", "invalid");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertAll(
                () -> assertNotNull(result),
                () -> assertEquals("Series", result.getSeriesName()),
                () -> assertTrue(result.getSeriesNumber() == null || result.getSeriesNumber() == 0.0f)
            );
        }
    }

    @Nested
    @DisplayName("ISBN Extraction Tests")
    class IsbnExtractionTests {

        @Test
        @DisplayName("Should extract ISBN-13 from EPUB metadata")
        void extractMetadata_withIsbn13_returnsIsbn13() throws IOException {
            File epubFile = createEpubWithIsbn("9781234567890", null);

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals("9781234567890", result.getIsbn13());
        }

        @Test
        @DisplayName("Should extract ISBN-10 from EPUB metadata")
        void extractMetadata_withIsbn10_returnsIsbn10() throws IOException {
            File epubFile = createEpubWithIsbn(null, "1234567890");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals("1234567890", result.getIsbn10());
        }

        @Test
        @DisplayName("Should extract both ISBN-13 and ISBN-10")
        void extractMetadata_withBothIsbns_returnsBoth() throws IOException {
            File epubFile = createEpubWithIsbn("9781234567890", "1234567890");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertAll(
                () -> assertNotNull(result),
                () -> assertEquals("9781234567890", result.getIsbn13()),
                () -> assertEquals("1234567890", result.getIsbn10())
            );
        }
    }

    @Nested
    @DisplayName("Cover Extraction Tests")
    class CoverExtractionTests {

        @Test
        @DisplayName("Should extract cover from EPUB when present")
        void extractCover_withCover_returnsCoverBytes() throws IOException {
            byte[] pngImage = createMinimalPngImage();
            File epubFile = createEpubWithCover(pngImage);

            byte[] cover = extractor.extractCover(epubFile);

            assertNotNull(cover);
            assertTrue(cover.length > 0);
            assertEquals(pngImage.length, cover.length);
        }

        @Test
        @DisplayName("Should return null for EPUB without cover")
        void extractCover_noCover_returnsNull() throws IOException {
            File epubFile = createMinimalEpub();

            byte[] cover = extractor.extractCover(epubFile);

            assertNull(cover);
        }

        @Test
        @DisplayName("Should return null for invalid file")
        void extractCover_invalidFile_returnsNull() throws IOException {
            File invalidFile = tempDir.resolve("invalid.epub").toFile();
            try (FileOutputStream fos = new FileOutputStream(invalidFile)) {
                fos.write("this is not an epub file".getBytes(StandardCharsets.UTF_8));
            }

            byte[] cover = extractor.extractCover(invalidFile);

            assertNull(cover);
        }

        @Test
        @DisplayName("Should extract cover declared with properties='cover-image' even if ID/href doesn't contain 'cover'")
        void extractCover_propertiesCoverImage_returnsCoverBytes() throws IOException {
            byte[] pngImage = createMinimalPngImage();
            // Use an ID and HREF that do not contain "cover"
            File epubFile = createEpubWithPropertiesCover(pngImage, "image123", "images/img001.png");

            byte[] cover = extractor.extractCover(epubFile);

            assertNotNull(cover, "Cover should be extracted");
            assertTrue(cover.length > 0);
            assertEquals(pngImage.length, cover.length);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return null for non-existent file")
        void extractMetadata_nonExistentFile_returnsNull() {
            File nonExistentFile = new File(tempDir.toFile(), "does-not-exist.epub");

            BookMetadata result = extractor.extractMetadata(nonExistentFile);

            assertNull(result);
        }

        @Test
        @DisplayName("Should return null for invalid EPUB structure")
        void extractMetadata_invalidEpub_returnsNull() throws IOException {
            File invalidFile = tempDir.resolve("invalid.epub").toFile();
            try (FileOutputStream fos = new FileOutputStream(invalidFile)) {
                fos.write("this is not an epub file".getBytes(StandardCharsets.UTF_8));
            }

            BookMetadata result = extractor.extractMetadata(invalidFile);

            assertNull(result);
        }

        @Test
        @DisplayName("Should handle EPUB with missing container.xml")
        void extractMetadata_missingContainer_returnsNull() throws IOException {
            File epubFile = tempDir.resolve("no-container.epub").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epubFile))) {
                zos.putNextEntry(new ZipEntry("mimetype"));
                zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNull(result);
        }
    }


    private File createMinimalEpub() throws IOException {
        return createEpubWithMetadata(DEFAULT_TITLE, null, null, null);
    }

    private File createEpubWithDate(String date) throws IOException {
        String opfContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    <dc:date>%s</dc:date>
                </metadata>
            </package>
            """, date);
        return createEpubWithOpf(opfContent, "test-" + date.hashCode() + ".epub");
    }

    private File createEpubWithMetadata(String title, String author, String publisher, String language) throws IOException {
        StringBuilder metadata = new StringBuilder();
        metadata.append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">");

        if (title != null) {
            metadata.append(String.format("<dc:title>%s</dc:title>", title));
        }
        if (author != null) {
            metadata.append(String.format("<dc:creator>%s</dc:creator>", author));
        }
        if (publisher != null) {
            metadata.append(String.format("<dc:publisher>%s</dc:publisher>", publisher));
        }
        if (language != null) {
            metadata.append(String.format("<dc:language>%s</dc:language>", language));
        }

        metadata.append("</metadata>");

        String opfContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                %s
            </package>
            """, metadata);

        String filename = "test-" + System.nanoTime() + ".epub";
        return createEpubWithOpf(opfContent, filename);
    }

    private File createEpubWithMultipleAuthors(String title, String author1, String author2) throws IOException {
        String opfContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>%s</dc:title>
                    <dc:creator>%s</dc:creator>
                    <dc:creator>%s</dc:creator>
                </metadata>
            </package>
            """, title, author1, author2);
        return createEpubWithOpf(opfContent, "test-multiauthor-" + System.nanoTime() + ".epub");
    }

    private File createEpubWithExtraCreators(String title, String author1, String author2, String illustrator, String editor) throws IOException {
        String opfContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                    <dc:title>%s</dc:title>
                    <dc:creator>%s</dc:creator>
                    <dc:creator opf:role="aut">%s</dc:creator>
                    <dc:creator opf:role="ill">%s</dc:creator>
                    <dc:creator id="creator04">%s</dc:creator>
                    <meta property="role" refines="#creator04" scheme="marc:relators">edt</meta>
                </metadata>
            </package>
            """, title, author1, author2, illustrator, editor);
        return createEpubWithOpf(opfContent, "test-extracreator-" + System.nanoTime() + ".epub");
    }

    private File createEpubWithCalibreSeries(String title, String seriesName, String seriesIndex) throws IOException {
        String opfContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>%s</dc:title>
                    <meta name="calibre:series" content="%s"/>
                    <meta name="calibre:series_index" content="%s"/>
                </metadata>
            </package>
            """, title, seriesName, seriesIndex);
        return createEpubWithOpf(opfContent, "test-calibre-series-" + System.nanoTime() + ".epub");
    }

    private File createEpubWithBookloreSeries(String title, String seriesName, String seriesIndex) throws IOException {
        String opfContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>%s</dc:title>
                    <meta property="booklore:series">%s</meta>
                    <meta property="booklore:series_index">%s</meta>
                </metadata>
            </package>
            """, title, seriesName, seriesIndex);
        return createEpubWithOpf(opfContent, "test-booklore-series-" + System.nanoTime() + ".epub");
    }

    private File createEpubWithIsbn(String isbn13, String isbn10) throws IOException {
        StringBuilder identifiers = new StringBuilder();
        if (isbn13 != null) {
            identifiers.append(String.format("<dc:identifier opf:scheme=\"ISBN\">%s</dc:identifier>", isbn13));
        }
        if (isbn10 != null) {
            identifiers.append(String.format("<dc:identifier opf:scheme=\"ISBN\">%s</dc:identifier>", isbn10));
        }

        String opfContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" xmlns:opf="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    %s
                </metadata>
            </package>
            """, identifiers);
        return createEpubWithOpf(opfContent, "test-isbn-" + System.nanoTime() + ".epub");
    }

    private File createEpubWithOpf(String opfContent, String filename) throws IOException {
        File epubFile = tempDir.resolve(filename).toFile();

        String containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
            """;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epubFile))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        return epubFile;
    }
    private byte[] createMinimalPngImage() {
        return new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x01,
            0x08, 0x06,
            0x00, 0x00, 0x00,
            (byte) 0x90, (byte) 0x77, (byte) 0x53, (byte) 0xDE,
            0x00, 0x00, 0x00, 0x0A,
            0x49, 0x44, 0x41, 0x54,
            0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
            0x00, 0x01,
            0x0D, (byte) 0x0A, 0x2D, (byte) 0xB4,
            0x00, 0x00, 0x00, 0x00,
            0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }

    private File createEpubWithCover(byte[] coverImageData) throws IOException {
        String opfContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <manifest>
                    <item id="cover" href="cover.png" media-type="image/png"/>
                </manifest>
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Book with Cover</dc:title>
                    <meta name="cover" content="cover"/>
                </metadata>
            </package>
            """;

        File epubFile = tempDir.resolve("test-cover-" + System.nanoTime() + ".epub").toFile();

        String containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
            """;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epubFile))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/cover.png"));
            zos.write(coverImageData);
            zos.closeEntry();
        }

        return epubFile;
    }

    private File createEpubWithPropertiesCover(byte[] coverImageData, String id, String href) throws IOException {
        String opfContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Book with Properties Cover</dc:title>
                </metadata>
                <manifest>
                    <item id="%s" href="%s" media-type="image/png" properties="cover-image"/>
                </manifest>
            </package>
            """, id, href);

        File epubFile = tempDir.resolve("test-prop-cover-" + System.nanoTime() + ".epub").toFile();

        String containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
            """;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epubFile))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/" + href));
            zos.write(coverImageData);
            zos.closeEntry();
        }

        return epubFile;
    }

    @Nested
    @DisplayName("URL Decoding Tests")
    class UrlDecodingTests {

        @Test
        @DisplayName("Should properly decode unicode characters in cover href")
        void extractCover_withUnicodeHref_decodesCorrectly() throws IOException {
            byte[] pngImage = createMinimalPngImage();
            String encodedHref = "cover%C3%A1.png";  // coverá.png URL-encoded
            File epubFile = createEpubWithUnicodeCover(pngImage, "cover_image", encodedHref);

            byte[] cover = extractor.extractCover(epubFile);

            assertNotNull(cover, "Cover should be extracted from EPUB with URL-encoded href");
            assertTrue(cover.length > 0);
        }

        @Test
        @DisplayName("Should extract cover with URL-encoded characters in manifest")
        void findCoverImageHrefInOpf_withEncodedHref_returnsDecodedPath() throws IOException {
            byte[] pngImage = createMinimalPngImage();
            String encodedHref = "images%2Fcover%C3%A1.jpg";  // images/coverá.jpg URL-encoded
            File epubFile = createEpubWithUnicodeCover(pngImage, "cover_image", encodedHref);

            byte[] cover = extractor.extractCover(epubFile);

            assertNotNull(cover, "Cover should be extracted even with encoded path");
            assertArrayEquals(pngImage, cover, "Extracted cover should match original image");
        }

        @Test
        @DisplayName("Should handle multiple encoded unicode characters")
        void extractCover_withMultipleEncodedChars_handlesCorrectly() throws IOException {
            byte[] pngImage = createMinimalPngImage();
            String encodedHref = "c%C3%B3ver%20t%C3%ADtle%20%C3%A1nd%20%C3%B1ame.png";
            File epubFile = createEpubWithUnicodeCover(pngImage, "multi_unicode_cover", encodedHref);

            byte[] cover = extractor.extractCover(epubFile);

            assertNotNull(cover, "Cover should be extracted from filename with multiple encoded chars");
            assertTrue(cover.length > 0);
        }
    }

    private File createEpubWithUnicodeCover(byte[] coverImageData, String id, String encodedHref) throws IOException {
        String opfContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Book with Unicode Cover</dc:title>
                </metadata>
                <manifest>
                    <item id="%s" href="%s" media-type="image/png" properties="cover-image"/>
                </manifest>
            </package>
            """, id, encodedHref);

        File epubFile = tempDir.resolve("test-unicode-cover-" + System.nanoTime() + ".epub").toFile();

        String containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
            """;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epubFile))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // The actual file path in the zip should match the decoded href
            String decodedPath = java.net.URLDecoder.decode(encodedHref, java.nio.charset.StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("OEBPS/" + decodedPath));
            zos.write(coverImageData);
            zos.closeEntry();
        }

        return epubFile;
    }
}

