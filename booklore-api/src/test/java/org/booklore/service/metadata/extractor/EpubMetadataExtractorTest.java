package org.booklore.service.metadata.extractor;

import org.booklore.model.dto.BookMetadata;
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
        @DisplayName("Should extract description from EPUB metadata")
        void extractMetadata_withDescription_returnsDescription() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test Book</dc:title>
                        <dc:description>A great book about testing.</dc:description>
                    </metadata>
                </package>
                """;
            File epubFile = createEpubWithOpf(opfContent, "test-desc-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epubFile);
            assertNotNull(result);
            assertEquals("A great book about testing.", result.getDescription());
        }

        @Test
        @DisplayName("Should extract multiple dc:subject categories from EPUB metadata")
        void extractMetadata_withCategories_returnsAllCategories() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test Book</dc:title>
                        <dc:subject>Science Fiction</dc:subject>
                        <dc:subject>Adventure</dc:subject>
                        <dc:subject>Fantasy</dc:subject>
                    </metadata>
                </package>
                """;
            File epubFile = createEpubWithOpf(opfContent, "test-cats-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epubFile);
            assertNotNull(result);
            assertNotNull(result.getCategories());
            assertTrue(result.getCategories().contains("Science Fiction"));
            assertTrue(result.getCategories().contains("Adventure"));
            assertTrue(result.getCategories().contains("Fantasy"));
            assertEquals(3, result.getCategories().size());
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

        @Test
        @DisplayName("Should extract EPUB3 belongs-to-collection series metadata")
        void extractMetadata_withEpub3Collection_returnsSeriesInfo() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test Book</dc:title>
                        <meta property="belongs-to-collection" id="c01">The Wheel of Time</meta>
                        <meta property="group-position" refines="#c01">14</meta>
                    </metadata>
                </package>
                """;
            File epubFile = createEpubWithOpf(opfContent, "test-epub3-series-" + System.nanoTime() + ".epub");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertAll(
                () -> assertNotNull(result),
                () -> assertEquals("The Wheel of Time", result.getSeriesName()),
                () -> assertEquals(14.0f, result.getSeriesNumber(), 0.001)
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

        @Test
        @DisplayName("Should extract formatted ISBN-10 as ISBN-10, not ISBN-13")
        void extractMetadata_withFormattedIsbn10_returnsIsbn10() throws IOException {
            File epubFile = createEpubWithIsbn(null, "90-206-1280-8");

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertNotNull(result);
            assertEquals("90-206-1280-8", result.getIsbn10());
            assertNull(result.getIsbn13(), "Should not set ISBN-13 for a formatted ISBN-10");
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
            File epubFile = createEpubWithPropertiesCover(pngImage, "image123", "images/img001.png");

            byte[] cover = extractor.extractCover(epubFile);

            assertNotNull(cover, "Cover should be extracted");
            assertTrue(cover.length > 0);
            assertEquals(pngImage.length, cover.length);
        }

        @Test
        @DisplayName("Should extract cover using meta name='cover' attribute fallback with URL-encoded href")
        void extractCover_metaCoverAttribute_returnsCoverBytes() throws IOException {
            byte[] pngImage = createMinimalPngImage();
            File epubFile = createEpubWithMetaCoverAttribute(pngImage, "image-id", "images/img001%2B.png");

            byte[] cover = extractor.extractCover(epubFile);

            assertNotNull(cover, "Cover should be extracted via meta cover attribute with encoded URL");
            assertTrue(cover.length > 0);
            assertArrayEquals(pngImage, cover);
        }

        @Test
        @DisplayName("Should extract cover using manifest heuristic fallback (href containing 'cover')")
        void extractCover_manifestHeuristic_returnsCoverBytes() throws IOException {
            byte[] pngImage = createMinimalPngImage();
            File epubFile = createEpubWithHeuristicManifestCover(pngImage, "some-id", "some-cover-file.png");

            byte[] cover = extractor.extractCover(epubFile);

            assertNotNull(cover, "Cover should be extracted via manifest heuristic");
            assertArrayEquals(pngImage, cover);
        }

        @Test
        @DisplayName("Should extract cover using ZIP heuristic fallback (ZIP entry containing 'cover')")
        void extractCover_zipHeuristic_returnsCoverBytes() throws IOException {
            byte[] pngImage = createMinimalPngImage();
            File epubFile = createEpubWithHeuristicZipCover(pngImage, "OEBPS/my-cool-cover.jpg");

            byte[] cover = extractor.extractCover(epubFile);

            assertNotNull(cover, "Cover should be extracted via ZIP heuristic");
            assertArrayEquals(pngImage, cover);
        }
    }

    @Nested
    @DisplayName("Calibre Identifier Tests")
    class CalibreIdentifierTests {

        @Test
        @DisplayName("Should extract ASIN from Calibre amazon: prefix identifier")
        void extractMetadata_calibreAmazonPrefix_returnsAsin() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier>amazon:B09QZY6N2K</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-asin-amazon-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("B09QZY6N2K", result.getAsin());
        }

        @Test
        @DisplayName("Should extract ASIN from Calibre asin: prefix identifier")
        void extractMetadata_calibreAsinPrefix_returnsAsin() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier>asin:B09QZY6N2K</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-asin-prefix-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("B09QZY6N2K", result.getAsin());
        }

        @Test
        @DisplayName("Should extract ASIN from mobi-asin: prefix identifier")
        void extractMetadata_calibreMobiAsinPrefix_returnsAsin() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier>mobi-asin:B09QZY6N2K</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-mobiasin-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("B09QZY6N2K", result.getAsin());
        }

        @Test
        @DisplayName("Should extract Goodreads ID from Calibre goodreads: prefix identifier")
        void extractMetadata_calibreGoodreadsPrefix_returnsGoodreadsId() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier>goodreads:60229814</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-gr-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("60229814", result.getGoodreadsId());
        }

        @Test
        @DisplayName("Should extract Google ID from Calibre google: prefix identifier")
        void extractMetadata_calibreGooglePrefix_returnsGoogleId() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier>google:abc123</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-google-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("abc123", result.getGoogleId());
        }

        @Test
        @DisplayName("Should extract Hardcover ID from Calibre hardcover: prefix identifier")
        void extractMetadata_calibreHardcoverPrefix_returnsHardcoverId() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier>hardcover:8888</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-hc-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("8888", result.getHardcoverId());
        }

        @Test
        @DisplayName("Should extract Hardcover Book ID from Calibre hardcover_book: prefix identifier")
        void extractMetadata_calibreHardcoverBookPrefix_returnsHardcoverBookId() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier>hardcover_book:7777</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-hcbook-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("7777", result.getHardcoverBookId());
        }

        @Test
        @DisplayName("Should extract Comicvine ID from Calibre comicvine: prefix identifier")
        void extractMetadata_calibreComicvinePrefix_returnsComicvineId() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier>comicvine:555</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-cv-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("555", result.getComicvineId());
        }

        @Test
        @DisplayName("Should extract Lubimyczytac ID from Calibre lubimyczytac: prefix identifier")
        void extractMetadata_calibreLubimyczytacPrefix_returnsLubimyczytacId() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier>lubimyczytac:444</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-lubi-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("444", result.getLubimyczytacId());
        }

        @Test
        @DisplayName("Should extract Ranobedb ID from Calibre ranobedb: prefix identifier")
        void extractMetadata_calibreRanobedbPrefix_returnsRanobedbId() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier>ranobedb:333</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-ranobedb-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("333", result.getRanobedbId());
        }

        @Test
        @DisplayName("Should extract ISBN-13 from urn:isbn: URN format")
        void extractMetadata_urnIsbn13_returnsIsbn13() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier>urn:isbn:9781234567890</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-urn-isbn-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("9781234567890", result.getIsbn13());
        }

        @Test
        @DisplayName("Should ignore calibre:UUID identifier")
        void extractMetadata_calibreUuid_isIgnored() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <dc:identifier id="BookId">calibre:12345678-abcd-1234-abcd-123456789abc</dc:identifier>
                    </metadata>
                </package>
                """;
            File epub = createEpubWithOpf(opfContent, "test-calibre-uuid-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertNull(result.getAsin());
            assertNull(result.getGoodreadsId());
        }
    }

    @Nested
    @DisplayName("Calibre User Metadata Tests")
    class CalibreUserMetadataTests {

        private File createEpubWithCalibreUserMetadata(String userMetadataJson) throws IOException {
            String escapedJson = userMetadataJson.replace("\"", "&quot;");
            String opfContent = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Calibre Book</dc:title>
                        <meta property="calibre:user_metadata">%s</meta>
                    </metadata>
                </package>
                """, escapedJson);
            return createEpubWithOpf(opfContent, "test-calibre-um-" + System.nanoTime() + ".epub");
        }

        @Test
        @DisplayName("Should extract subtitle from Calibre #subtitle custom column")
        void extractMetadata_calibreSubtitle_returnsSubtitle() throws IOException {
            String json = "{\"#subtitle\":{\"#value#\":\"The Hidden Path\"}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("The Hidden Path", result.getSubtitle());
        }

        @Test
        @DisplayName("Should extract page count from Calibre #pagecount custom column")
        void extractMetadata_calibrePagecount_returnsPageCount() throws IOException {
            String json = "{\"#pagecount\":{\"#value#\":412}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals(412, result.getPageCount());
        }

        @Test
        @DisplayName("Should extract series total from Calibre #series_total custom column")
        void extractMetadata_calibreSeriesTotal_returnsSeriesTotal() throws IOException {
            String json = "{\"#series_total\":{\"#value#\":7}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals(7, result.getSeriesTotal());
        }

        @Test
        @DisplayName("Should extract all ratings from Calibre custom columns")
        void extractMetadata_calibreRatings_returnsAllRatings() throws IOException {
            String json = "{" +
                "\"#amazon_rating\":{\"#value#\":4.5}," +
                "\"#amazon_review_count\":{\"#value#\":1234}," +
                "\"#goodreads_rating\":{\"#value#\":4.1}," +
                "\"#goodreads_review_count\":{\"#value#\":5678}," +
                "\"#hardcover_rating\":{\"#value#\":4.8}," +
                "\"#hardcover_review_count\":{\"#value#\":999}," +
                "\"#lubimyczytac_rating\":{\"#value#\":3.9}," +
                "\"#ranobedb_rating\":{\"#value#\":4.2}" +
                "}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertAll(
                () -> assertEquals(4.5, result.getAmazonRating(), 0.001),
                () -> assertEquals(1234, result.getAmazonReviewCount()),
                () -> assertEquals(4.1, result.getGoodreadsRating(), 0.001),
                () -> assertEquals(5678, result.getGoodreadsReviewCount()),
                () -> assertEquals(4.8, result.getHardcoverRating(), 0.001),
                () -> assertEquals(999, result.getHardcoverReviewCount()),
                () -> assertEquals(3.9, result.getLubimyczytacRating(), 0.001),
                () -> assertEquals(4.2, result.getRanobedbRating(), 0.001)
            );
        }

        @Test
        @DisplayName("Should extract age rating from Calibre #age_rating custom column")
        void extractMetadata_calibreAgeRating_returnsAgeRating() throws IOException {
            String json = "{\"#age_rating\":{\"#value#\":16}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals(16, result.getAgeRating());
        }

        @Test
        @DisplayName("Should extract valid content rating from Calibre #content_rating custom column")
        void extractMetadata_calibreContentRating_valid_returnsContentRating() throws IOException {
            String json = "{\"#content_rating\":{\"#value#\":\"Teen\"}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals("TEEN", result.getContentRating());
        }

        @Test
        @DisplayName("Should ignore invalid content rating from Calibre #content_rating custom column")
        void extractMetadata_calibreContentRating_invalid_isIgnored() throws IOException {
            String json = "{\"#content_rating\":{\"#value#\":\"Unknown Rating\"}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertNull(result.getContentRating());
        }

        @Test
        @DisplayName("Should extract moods from Calibre #moods custom column")
        void extractMetadata_calibreMoods_returnsMoods() throws IOException {
            String json = "{\"#moods\":{\"#value#\":\"Dark, Suspenseful, Atmospheric\"}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertNotNull(result.getMoods());
            assertTrue(result.getMoods().contains("Dark"));
            assertTrue(result.getMoods().contains("Suspenseful"));
            assertTrue(result.getMoods().contains("Atmospheric"));
        }

        @Test
        @DisplayName("Should extract tags from Calibre #extra_tags custom column")
        void extractMetadata_calibreExtraTags_returnsTags() throws IOException {
            String json = "{\"#extra_tags\":{\"#value#\":\"Fiction, Thriller, Mystery\"}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertNotNull(result.getTags());
            assertTrue(result.getTags().contains("Fiction"));
            assertTrue(result.getTags().contains("Thriller"));
            assertTrue(result.getTags().contains("Mystery"));
        }

        @Test
        @DisplayName("Should handle null #value# in Calibre user_metadata gracefully")
        void extractMetadata_calibreNullValue_isIgnored() throws IOException {
            String json = "{\"#subtitle\":{\"#value#\":null},\"#pagecount\":{\"#value#\":null}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertNull(result.getSubtitle());
            assertNull(result.getPageCount());
        }

        @Test
        @DisplayName("Should ignore age rating value not in valid set {0,6,10,13,16,18,21}")
        void extractMetadata_calibreAgeRating_invalidValue_isIgnored() throws IOException {
            String json = "{\"#age_rating\":{\"#value#\":15}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertNull(result.getAgeRating());
        }

        @Test
        @DisplayName("Should extract age rating 0 (All Ages) from Calibre #age_rating")
        void extractMetadata_calibreAgeRating_zero_returnsZero() throws IOException {
            String json = "{\"#age_rating\":{\"#value#\":0}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals(0, result.getAgeRating());
        }

        @Test
        @DisplayName("Should extract age rating 21 from Calibre #age_rating")
        void extractMetadata_calibreAgeRating_21_returns21() throws IOException {
            String json = "{\"#age_rating\":{\"#value#\":21}}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertEquals(21, result.getAgeRating());
        }

        @Test
        @DisplayName("Should extract age_rating and content_rating from a full real-world Calibre user_metadata JSON blob with 17 custom columns")
        void extractMetadata_calibreFullRealWorldJson_extractsAgeAndContentRating() throws IOException {
            String json = "{" +
                "\"#age_rating\":{\"#extra#\":null,\"#value#\":18,\"category_sort\":\"value\",\"colnum\":82,\"column\":\"value\",\"datatype\":\"int\",\"display\":{\"description\":\"Age Rating\",\"number_format\":null,\"web_search_template\":\"\"},\"is_category\":false,\"is_csp\":false,\"is_custom\":true,\"is_editable\":true,\"is_multiple\":null,\"is_multiple2\":{},\"kind\":\"field\",\"label\":\"age_rating\",\"link_column\":\"value\",\"name\":\"Age Rating\",\"rec_index\":23}," +
                "\"#content_rating\":{\"#extra#\":null,\"#value#\":\"Everyone\",\"category_sort\":\"value\",\"colnum\":83,\"column\":\"value\",\"datatype\":\"text\",\"display\":{\"description\":\"Content Rating\",\"use_decorations\":false,\"web_search_template\":\"\"},\"is_category\":true,\"is_csp\":false,\"is_custom\":true,\"is_editable\":true,\"is_multiple\":null,\"is_multiple2\":{},\"kind\":\"field\",\"label\":\"content_rating\",\"link_column\":\"value\",\"name\":\"Content Rating\",\"rec_index\":26}," +
                "\"#amazon_rating\":{\"#extra#\":null,\"#value#\":5.0,\"datatype\":\"float\",\"colnum\":67,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#amazon_review_count\":{\"#extra#\":null,\"#value#\":5,\"datatype\":\"int\",\"colnum\":68,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#goodreads_rating\":{\"#extra#\":null,\"#value#\":5.0,\"datatype\":\"float\",\"colnum\":70,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#goodreads_review_count\":{\"#extra#\":null,\"#value#\":5,\"datatype\":\"int\",\"colnum\":71,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#hardcover_rating\":{\"#extra#\":null,\"#value#\":5.0,\"datatype\":\"float\",\"colnum\":72,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#hardcover_review_count\":{\"#extra#\":null,\"#value#\":5,\"datatype\":\"int\",\"colnum\":73,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#lubimyczytac_rating\":{\"#extra#\":null,\"#value#\":5.0,\"datatype\":\"float\",\"colnum\":84,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#ranobedb_rating\":{\"#extra#\":null,\"#value#\":5.0,\"datatype\":\"float\",\"colnum\":85,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#pagecount\":{\"#extra#\":null,\"#value#\":1000,\"datatype\":\"int\",\"colnum\":81,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#series_total\":{\"#extra#\":null,\"#value#\":10,\"datatype\":\"int\",\"colnum\":37,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#subtitle\":{\"#extra#\":null,\"#value#\":\"DEMO-SUBTITLE\",\"datatype\":\"comments\",\"colnum\":77,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#moods\":{\"#extra#\":null,\"#value#\":[\"DEMO-MOODS\"],\"datatype\":\"text\",\"colnum\":76,\"is_multiple\":\"|\",\"is_multiple2\":{\"cache_to_list\":\"|\",\"list_to_ui\":\", \",\"ui_to_list\":\",\"}}," +
                "\"#extra_tags\":{\"#extra#\":null,\"#value#\":[\"DEMO-TAGS\"],\"datatype\":\"text\",\"colnum\":74,\"is_multiple\":\"|\",\"is_multiple2\":{\"cache_to_list\":\"|\",\"list_to_ui\":\", \",\"ui_to_list\":\",\"}}," +
                "\"#word_count\":{\"#extra#\":null,\"#value#\":1000,\"datatype\":\"int\",\"colnum\":57,\"is_multiple\":null,\"is_multiple2\":{}}," +
                "\"#goodreads_awards\":{\"#extra#\":null,\"#value#\":[\"DEMO-AWARDS\"],\"datatype\":\"text\",\"colnum\":69,\"is_multiple\":\"|\",\"is_multiple2\":{}}" +
                "}";
            File epub = createEpubWithCalibreUserMetadata(json);
            BookMetadata result = extractor.extractMetadata(epub);
            assertNotNull(result);
            assertAll(
                () -> assertEquals(18, result.getAgeRating()),
                () -> assertEquals("EVERYONE", result.getContentRating()),
                () -> assertEquals(5.0, result.getAmazonRating(), 0.001),
                () -> assertEquals(5, result.getAmazonReviewCount()),
                () -> assertEquals(1000, result.getPageCount()),
                () -> assertEquals(10, result.getSeriesTotal()),
                () -> assertEquals("DEMO-SUBTITLE", result.getSubtitle()),
                () -> assertNotNull(result.getMoods()),
                () -> assertTrue(result.getMoods().contains("DEMO-MOODS")),
                () -> assertNotNull(result.getTags()),
                () -> assertTrue(result.getTags().contains("DEMO-TAGS"))
            );
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

    @Nested
    @DisplayName("BookLore Round-Trip Metadata Tests")
    class BookloreRoundTripTests {

        @Test
        @DisplayName("Should extract all 14 booklore: namespace fields from OPF")
        void extractMetadata_allBookloreFields_roundTrip() throws IOException {
            File epubFile = createEpubWithAllBookloreFields();

            BookMetadata result = extractor.extractMetadata(epubFile);

            assertAll(
                () -> assertNotNull(result),
                // IDs (legacy booklore meta format)
                () -> assertEquals("B001", result.getAsin()),
                () -> assertEquals("1001", result.getGoodreadsId()),
                () -> assertEquals("2002", result.getComicvineId()),
                () -> assertEquals("3003", result.getHardcoverId()),
                () -> assertEquals("4004", result.getRanobedbId()),
                () -> assertEquals("5005", result.getGoogleId()),
                () -> assertEquals("6006", result.getLubimyczytacId()),
                // Numeric fields
                () -> assertEquals(350, result.getPageCount()),
                () -> assertEquals(10, result.getSeriesTotal()),
                () -> assertEquals(16, result.getAgeRating()),
                () -> assertEquals("TEEN", result.getContentRating()),
                // Ratings
                () -> assertEquals(4.5, result.getAmazonRating(), 0.001),
                () -> assertEquals(4.0, result.getGoodreadsRating(), 0.001),
                () -> assertEquals(5.0, result.getHardcoverRating(), 0.001),
                () -> assertEquals(3.5, result.getLubimyczytacRating(), 0.001),
                () -> assertEquals(2.0, result.getRanobedbRating(), 0.001),
                // Review counts
                () -> assertEquals(1234, result.getAmazonReviewCount()),
                () -> assertEquals(5678, result.getGoodreadsReviewCount()),
                () -> assertEquals(999, result.getHardcoverReviewCount()),
                // Sets (JSON array format - as written by EpubMetadataWriter)
                () -> assertNotNull(result.getMoods()),
                () -> assertTrue(result.getMoods().contains("Dark")),
                () -> assertTrue(result.getMoods().contains("Mystery")),
                () -> assertNotNull(result.getTags()),
                () -> assertTrue(result.getTags().contains("Fiction")),
                () -> assertTrue(result.getTags().contains("Thriller"))
            );
        }

        @Test
        @DisplayName("Should extract booklore:subtitle from OPF")
        void extractMetadata_bookloreSubtitle_returnsSubtitle() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Main Title</dc:title>
                        <meta property="booklore:subtitle">A Subtitle</meta>
                    </metadata>
                </package>
                """;
            File epubFile = createEpubWithOpf(opfContent, "test-subtitle-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epubFile);
            assertNotNull(result);
            assertEquals("A Subtitle", result.getSubtitle());
        }

        @Test
        @DisplayName("Should extract booklore:age_rating as integer")
        void extractMetadata_bookloreAgeRating_returnsInteger() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <meta property="booklore:age_rating">18</meta>
                    </metadata>
                </package>
                """;
            File epubFile = createEpubWithOpf(opfContent, "test-agerate-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epubFile);
            assertNotNull(result);
            assertEquals(18, result.getAgeRating());
        }

        @Test
        @DisplayName("Should extract booklore:content_rating as valid enum string")
        void extractMetadata_bookloreContentRating_returnsString() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <meta property="booklore:content_rating">MATURE</meta>
                    </metadata>
                </package>
                """;
            File epubFile = createEpubWithOpf(opfContent, "test-contentrate-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epubFile);
            assertNotNull(result);
            assertEquals("MATURE", result.getContentRating());
        }

        @Test
        @DisplayName("Should extract booklore moods and tags as JSON arrays")
        void extractMetadata_bookloreMoodsTagsJsonArray_returnsSets() throws IOException {
            String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test</dc:title>
                        <meta property="booklore:moods">["Dark", "Tense", "Atmospheric"]</meta>
                        <meta property="booklore:tags">["Horror", "Suspense"]</meta>
                    </metadata>
                </package>
                """;
            File epubFile = createEpubWithOpf(opfContent, "test-moodstags-" + System.nanoTime() + ".epub");
            BookMetadata result = extractor.extractMetadata(epubFile);
            assertNotNull(result);
            assertNotNull(result.getMoods());
            assertTrue(result.getMoods().contains("Dark"));
            assertTrue(result.getMoods().contains("Tense"));
            assertTrue(result.getMoods().contains("Atmospheric"));
            assertNotNull(result.getTags());
            assertTrue(result.getTags().contains("Horror"));
            assertTrue(result.getTags().contains("Suspense"));
        }
    }

    private File createEpubWithAllBookloreFields() throws IOException {
        String opfContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0"
                     prefix="booklore: http://booklore.org/metadata/1.0/">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>BookLore Round-Trip Test</dc:title>
                    <meta property="booklore:asin">B001</meta>
                    <meta property="booklore:goodreads_id">1001</meta>
                    <meta property="booklore:comicvine_id">2002</meta>
                    <meta property="booklore:hardcover_id">3003</meta>
                    <meta property="booklore:ranobedb_id">4004</meta>
                    <meta property="booklore:google_books_id">5005</meta>
                    <meta property="booklore:lubimyczytac_id">6006</meta>
                    <meta property="booklore:page_count">350</meta>
                    <meta property="booklore:series_total">10</meta>
                    <meta property="booklore:age_rating">16</meta>
                    <meta property="booklore:content_rating">TEEN</meta>
                    <meta property="booklore:amazon_rating">4.5</meta>
                    <meta property="booklore:amazon_review_count">1234</meta>
                    <meta property="booklore:goodreads_rating">4.0</meta>
                    <meta property="booklore:goodreads_review_count">5678</meta>
                    <meta property="booklore:hardcover_rating">5.0</meta>
                    <meta property="booklore:hardcover_review_count">999</meta>
                    <meta property="booklore:lubimyczytac_rating">3.5</meta>
                    <meta property="booklore:ranobedb_rating">2.0</meta>
                    <meta property="booklore:moods">["Dark", "Mystery"]</meta>
                    <meta property="booklore:tags">["Fiction", "Thriller"]</meta>
                </metadata>
            </package>
            """;
        return createEpubWithOpf(opfContent, "test-booklore-all-" + System.nanoTime() + ".epub");
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

    private File createEpubWithMetaCoverAttribute(byte[] coverImageData, String id, String encodedHref) throws IOException {
        String opfContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Book with Meta Cover Attribute</dc:title>
                    <meta name="cover" content="%s"/>
                </metadata>
                <manifest>
                    <item id="%s" href="%s" media-type="image/png"/>
                </manifest>
            </package>
            """, id, id, encodedHref);

        File epubFile = tempDir.resolve("test-meta-cover-" + System.nanoTime() + ".epub").toFile();

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

            String decodedPath = java.net.URLDecoder.decode(encodedHref, StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("OEBPS/" + decodedPath));
            zos.write(coverImageData);
            zos.closeEntry();
        }

        return epubFile;
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

            String decodedPath = java.net.URLDecoder.decode(encodedHref, java.nio.charset.StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("OEBPS/" + decodedPath));
            zos.write(coverImageData);
            zos.closeEntry();
        }

        return epubFile;
    }

    private File createEpubWithHeuristicManifestCover(byte[] coverImageData, String id, String href) throws IOException {
        String opfContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Book with Heuristic Manifest Cover</dc:title>
                </metadata>
                <manifest>
                    <item id="%s" href="%s" media-type="image/png"/>
                </manifest>
            </package>
            """, id, href);

        File epubFile = tempDir.resolve("test-heuristic-manifest-" + System.nanoTime() + ".epub").toFile();

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

    private File createEpubWithHeuristicZipCover(byte[] coverImageData, String path) throws IOException {
        File epubFile = tempDir.resolve("test-heuristic-zip-" + System.nanoTime() + ".epub").toFile();

        String containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
            """;

        String opfContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Book with Heuristic ZIP Cover</dc:title>
                </metadata>
            </package>
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

            zos.putNextEntry(new ZipEntry(path));
            zos.write(coverImageData);
            zos.closeEntry();
        }

        return epubFile;
    }
}

