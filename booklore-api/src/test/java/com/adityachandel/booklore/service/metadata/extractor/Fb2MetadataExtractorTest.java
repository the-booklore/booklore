package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class Fb2MetadataExtractorTest {

    private static final String DEFAULT_TITLE = "The Seven Poor Travellers";
    private static final String DEFAULT_AUTHOR_FIRST = "Charles";
    private static final String DEFAULT_AUTHOR_LAST = "Dickens";
    private static final String DEFAULT_AUTHOR_FULL = "Charles Dickens";
    private static final String DEFAULT_GENRE = "antique";
    private static final String DEFAULT_LANGUAGE = "ru";
    private static final String DEFAULT_PUBLISHER = "Test Publisher";
    private static final String DEFAULT_ISBN = "9781234567890";
    private static final String DEFAULT_SERIES = "Great Works";

    private Fb2MetadataExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new Fb2MetadataExtractor();
    }

    @Nested
    @DisplayName("Basic Metadata Extraction Tests")
    class BasicMetadataTests {

        @Test
        @DisplayName("Should extract title from title-info")
        void extractMetadata_withTitle_returnsTitle() throws IOException {
            String fb2Content = createFb2WithTitleInfo(
                DEFAULT_TITLE,
                DEFAULT_AUTHOR_FIRST,
                DEFAULT_AUTHOR_LAST,
                DEFAULT_GENRE,
                DEFAULT_LANGUAGE
            );
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals(DEFAULT_TITLE, result.getTitle());
        }

        @Test
        @DisplayName("Should extract author name from title-info")
        void extractMetadata_withAuthor_returnsAuthor() throws IOException {
            String fb2Content = createFb2WithTitleInfo(
                DEFAULT_TITLE,
                DEFAULT_AUTHOR_FIRST,
                DEFAULT_AUTHOR_LAST,
                DEFAULT_GENRE,
                DEFAULT_LANGUAGE
            );
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertNotNull(result.getAuthors());
            assertEquals(1, result.getAuthors().size());
            assertTrue(result.getAuthors().contains(DEFAULT_AUTHOR_FULL));
        }

        @Test
        @DisplayName("Should extract multiple authors")
        void extractMetadata_withMultipleAuthors_returnsAllAuthors() throws IOException {
            String fb2Content = createFb2WithMultipleAuthors();
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertNotNull(result.getAuthors());
            assertEquals(2, result.getAuthors().size());
            assertTrue(result.getAuthors().contains("Charles Dickens"));
            assertTrue(result.getAuthors().contains("Jane Austen"));
        }

        @Test
        @DisplayName("Should extract genre as category")
        void extractMetadata_withGenre_returnsCategory() throws IOException {
            String fb2Content = createFb2WithTitleInfo(
                DEFAULT_TITLE,
                DEFAULT_AUTHOR_FIRST,
                DEFAULT_AUTHOR_LAST,
                DEFAULT_GENRE,
                DEFAULT_LANGUAGE
            );
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertNotNull(result.getCategories());
            assertTrue(result.getCategories().contains(DEFAULT_GENRE));
        }

        @Test
        @DisplayName("Should extract multiple genres as categories")
        void extractMetadata_withMultipleGenres_returnsAllCategories() throws IOException {
            String fb2Content = createFb2WithMultipleGenres();
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertNotNull(result.getCategories());
            assertTrue(result.getCategories().contains("fiction"));
            assertTrue(result.getCategories().contains("drama"));
        }

        @Test
        @DisplayName("Should extract language")
        void extractMetadata_withLanguage_returnsLanguage() throws IOException {
            String fb2Content = createFb2WithTitleInfo(
                DEFAULT_TITLE,
                DEFAULT_AUTHOR_FIRST,
                DEFAULT_AUTHOR_LAST,
                DEFAULT_GENRE,
                DEFAULT_LANGUAGE
            );
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals(DEFAULT_LANGUAGE, result.getLanguage());
        }

        @Test
        @DisplayName("Should extract annotation as description")
        void extractMetadata_withAnnotation_returnsDescription() throws IOException {
            String annotation = "This is a test book description";
            String fb2Content = createFb2WithAnnotation(annotation);
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertNotNull(result.getDescription());
            assertTrue(result.getDescription().contains(annotation));
        }
    }

    @Nested
    @DisplayName("Date Extraction Tests")
    class DateExtractionTests {

        @Test
        @DisplayName("Should extract date from title-info")
        void extractMetadata_withDate_returnsDate() throws IOException {
            String fb2Content = createFb2WithDate("2024-06-15");
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals(LocalDate.of(2024, 6, 15), result.getPublishedDate());
        }

        @Test
        @DisplayName("Should extract year-only date")
        void extractMetadata_withYearOnly_returnsDateWithJanuary1st() throws IOException {
            String fb2Content = createFb2WithDate("2024");
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals(LocalDate.of(2024, 1, 1), result.getPublishedDate());
        }

        @Test
        @DisplayName("Should handle date with value attribute")
        void extractMetadata_withDateValue_returnsDate() throws IOException {
            String fb2Content = createFb2WithDateValue("2024-06-15", "June 15, 2024");
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals(LocalDate.of(2024, 6, 15), result.getPublishedDate());
        }
    }

    @Nested
    @DisplayName("Series Metadata Tests")
    class SeriesMetadataTests {

        @Test
        @DisplayName("Should extract series name from sequence")
        void extractMetadata_withSequence_returnsSeriesName() throws IOException {
            String fb2Content = createFb2WithSequence(DEFAULT_SERIES, "3");
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals(DEFAULT_SERIES, result.getSeriesName());
        }

        @Test
        @DisplayName("Should extract series number from sequence")
        void extractMetadata_withSequence_returnsSeriesNumber() throws IOException {
            String fb2Content = createFb2WithSequence(DEFAULT_SERIES, "3");
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals(3.0f, result.getSeriesNumber(), 0.001);
        }

        @Test
        @DisplayName("Should handle decimal series numbers")
        void extractMetadata_withDecimalSequence_returnsDecimalSeriesNumber() throws IOException {
            String fb2Content = createFb2WithSequence(DEFAULT_SERIES, "2.5");
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals(2.5f, result.getSeriesNumber(), 0.001);
        }
    }

    @Nested
    @DisplayName("Publisher Info Extraction Tests")
    class PublisherInfoTests {

        @Test
        @DisplayName("Should extract publisher from publish-info")
        void extractMetadata_withPublisher_returnsPublisher() throws IOException {
            String fb2Content = createFb2WithPublishInfo(DEFAULT_PUBLISHER, "2024", DEFAULT_ISBN);
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals(DEFAULT_PUBLISHER, result.getPublisher());
        }

        @Test
        @DisplayName("Should extract year from publish-info")
        void extractMetadata_withPublishYear_returnsDate() throws IOException {
            String fb2Content = createFb2WithPublishInfo(DEFAULT_PUBLISHER, "2024", null);
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals(LocalDate.of(2024, 1, 1), result.getPublishedDate());
        }

        @Test
        @DisplayName("Should extract ISBN-13 from publish-info")
        void extractMetadata_withIsbn13_returnsIsbn13() throws IOException {
            String fb2Content = createFb2WithPublishInfo(null, null, "9781234567890");
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals("9781234567890", result.getIsbn13());
        }

        @Test
        @DisplayName("Should extract ISBN-10 from publish-info")
        void extractMetadata_withIsbn10_returnsIsbn10() throws IOException {
            String fb2Content = createFb2WithPublishInfo(null, null, "1234567890");
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertEquals("1234567890", result.getIsbn10());
        }
    }

    @Nested
    @DisplayName("Keywords Extraction Tests")
    class KeywordsTests {

        @Test
        @DisplayName("Should extract keywords as categories")
        void extractMetadata_withKeywords_returnsCategories() throws IOException {
            String fb2Content = createFb2WithKeywords("adventure, mystery, thriller");
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertNotNull(result.getCategories());
            assertTrue(result.getCategories().contains("adventure"));
            assertTrue(result.getCategories().contains("mystery"));
            assertTrue(result.getCategories().contains("thriller"));
        }

        @Test
        @DisplayName("Should handle keywords with semicolon separator")
        void extractMetadata_withSemicolonKeywords_returnsCategories() throws IOException {
            String fb2Content = createFb2WithKeywords("adventure; mystery; thriller");
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertNotNull(result.getCategories());
            assertTrue(result.getCategories().contains("adventure"));
            assertTrue(result.getCategories().contains("mystery"));
            assertTrue(result.getCategories().contains("thriller"));
        }
    }

    @Nested
    @DisplayName("Author Name Extraction Tests")
    class AuthorNameTests {

        @Test
        @DisplayName("Should extract author with first and last name")
        void extractMetadata_withFirstAndLastName_returnsFullName() throws IOException {
            String fb2Content = createFb2WithAuthorNames("John", null, "Doe", null);
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertTrue(result.getAuthors().contains("John Doe"));
        }

        @Test
        @DisplayName("Should extract author with first, middle and last name")
        void extractMetadata_withMiddleName_returnsFullNameWithMiddle() throws IOException {
            String fb2Content = createFb2WithAuthorNames("John", "Robert", "Doe", null);
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertTrue(result.getAuthors().contains("John Robert Doe"));
        }

        @Test
        @DisplayName("Should use nickname when name parts are missing")
        void extractMetadata_withNicknameOnly_returnsNickname() throws IOException {
            String fb2Content = createFb2WithAuthorNames(null, null, null, "WriterPro");
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertNotNull(result);
            assertTrue(result.getAuthors().contains("WriterPro"));
        }
    }

    @Nested
    @DisplayName("Cover Extraction Tests")
    class CoverExtractionTests {

        @Test
        @DisplayName("Should extract cover image from binary section")
        void extractCover_withCoverImage_returnsCoverBytes() throws IOException {
            byte[] imageData = createMinimalPngImage();
            String fb2Content = createFb2WithCover(imageData);
            File fb2File = createFb2File(fb2Content);

            byte[] result = extractor.extractCover(fb2File);

            assertNotNull(result);
            assertTrue(result.length > 0);
        }

        @Test
        @DisplayName("Should return null when no cover present")
        void extractCover_noCover_returnsNull() throws IOException {
            String fb2Content = createMinimalFb2();
            File fb2File = createFb2File(fb2Content);

            byte[] result = extractor.extractCover(fb2File);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Complete Metadata Extraction Test")
    class CompleteMetadataTest {

        @Test
        @DisplayName("Should extract all metadata fields from complete FB2 with title-info")
        void extractMetadata_completeFile_extractsAllFields() throws IOException {
            String fb2Content = createCompleteFb2();
            File fb2File = createFb2File(fb2Content);

            BookMetadata result = extractor.extractMetadata(fb2File);

            assertAll(
                () -> assertNotNull(result, "Metadata should not be null"),
                () -> assertEquals("Pride and Prejudice", result.getTitle(), "Title should be extracted"),
                () -> assertNotNull(result.getAuthors(), "Authors should not be null"),
                () -> assertEquals(1, result.getAuthors().size(), "Should have one author"),
                () -> assertTrue(result.getAuthors().contains("Jane Austen"), "Should contain full author name"),
                () -> assertNotNull(result.getCategories(), "Categories should not be null"),
                () -> assertTrue(result.getCategories().contains("romance"), "Should contain genre"),
                () -> assertEquals("en", result.getLanguage(), "Language should be extracted"),
                () -> assertNotNull(result.getDescription(), "Description should not be null"),
                () -> assertTrue(result.getDescription().contains("classic novel"), "Description should contain annotation text"),
                () -> assertEquals(LocalDate.of(1813, 1, 1), result.getPublishedDate(), "Published date should be extracted"),
                () -> assertEquals("T. Egerton", result.getPublisher(), "Publisher should be extracted"),
                () -> assertEquals("Classic Literature Series", result.getSeriesName(), "Series name should be extracted"),
                () -> assertEquals(2.0f, result.getSeriesNumber(), 0.001, "Series number should be extracted")
            );
        }

        private String createCompleteFb2() {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:xlink="http://www.w3.org/1999/xlink">
                  <description>
                    <title-info>
                      <genre>romance</genre>
                      <author>
                        <first-name>Jane</first-name>
                        <last-name>Austen</last-name>
                      </author>
                      <book-title>Pride and Prejudice</book-title>
                      <annotation>
                        <p>Pride and Prejudice is a classic novel by Jane Austen, first published in 1813. It is a romantic novel of manners that follows the character development of Elizabeth Bennet.</p>
                        <p>The novel deals with issues of morality, education, and marriage in the society of the landed gentry of the British Regency. Elizabeth must learn the error of making hasty judgments and come to appreciate the difference between superficial goodness and actual goodness.</p>
                      </annotation>
                      <keywords>romance, regency, england, bennet, darcy, marriage</keywords>
                      <date value="1813-01-01">1813</date>
                      <lang>en</lang>
                      <sequence name="Classic Literature Series" number="2"/>
                    </title-info>
                    <document-info>
                      <author>
                        <nickname>TestUser</nickname>
                      </author>
                      <date value="2024-01-01">January 1, 2024</date>
                      <id>TestUser_PrideAndPrejudice_12345</id>
                      <version>2.0</version>
                    </document-info>
                    <publish-info>
                      <book-name>Pride and Prejudice</book-name>
                      <publisher>T. Egerton</publisher>
                      <city>London</city>
                      <year>1813</year>
                    </publish-info>
                  </description>
                  <body>
                    <section>
                      <title>
                        <p>Chapter 1</p>
                      </title>
                      <p>It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.</p>
                    </section>
                  </body>
                </FictionBook>
                """;
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty FB2 file gracefully")
        void extractMetadata_emptyFile_returnsNull() throws IOException {
            File emptyFile = tempDir.resolve("empty.fb2").toFile();
            try (FileOutputStream fos = new FileOutputStream(emptyFile)) {
                fos.write("".getBytes(StandardCharsets.UTF_8));
            }

            BookMetadata result = extractor.extractMetadata(emptyFile);

            assertNull(result);
        }

        @Test
        @DisplayName("Should handle invalid XML gracefully")
        void extractMetadata_invalidXml_returnsNull() throws IOException {
            File invalidFile = tempDir.resolve("invalid.fb2").toFile();
            try (FileOutputStream fos = new FileOutputStream(invalidFile)) {
                fos.write("this is not valid XML".getBytes(StandardCharsets.UTF_8));
            }

            BookMetadata result = extractor.extractMetadata(invalidFile);

            assertNull(result);
        }

        @Test
        @DisplayName("Should handle non-existent file gracefully")
        void extractMetadata_nonExistentFile_returnsNull() {
            File nonExistent = new File(tempDir.toFile(), "does-not-exist.fb2");

            BookMetadata result = extractor.extractMetadata(nonExistent);

            assertNull(result);
        }
    }

    // Helper methods to create FB2 test files

    private String createMinimalFb2() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>fiction</genre>
                  <author>
                    <first-name>Test</first-name>
                    <last-name>Author</last-name>
                  </author>
                  <book-title>Test Book</book-title>
                  <lang>en</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Test content</p>
                </section>
              </body>
            </FictionBook>
            """;
    }

    private String createFb2WithTitleInfo(String title, String firstName, String lastName, String genre, String lang) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>%s</genre>
                  <author>
                    <first-name>%s</first-name>
                    <last-name>%s</last-name>
                  </author>
                  <book-title>%s</book-title>
                  <lang>%s</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Content</p>
                </section>
              </body>
            </FictionBook>
            """, genre, firstName, lastName, title, lang);
    }

    private String createFb2WithMultipleAuthors() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>fiction</genre>
                  <author>
                    <first-name>Charles</first-name>
                    <last-name>Dickens</last-name>
                  </author>
                  <author>
                    <first-name>Jane</first-name>
                    <last-name>Austen</last-name>
                  </author>
                  <book-title>Collaborative Work</book-title>
                  <lang>en</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Content</p>
                </section>
              </body>
            </FictionBook>
            """;
    }

    private String createFb2WithMultipleGenres() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>fiction</genre>
                  <genre>drama</genre>
                  <author>
                    <first-name>Test</first-name>
                    <last-name>Author</last-name>
                  </author>
                  <book-title>Multi-Genre Book</book-title>
                  <lang>en</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Content</p>
                </section>
              </body>
            </FictionBook>
            """;
    }

    private String createFb2WithAnnotation(String annotation) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>fiction</genre>
                  <author>
                    <first-name>Test</first-name>
                    <last-name>Author</last-name>
                  </author>
                  <book-title>Book with Annotation</book-title>
                  <annotation>
                    <p>%s</p>
                  </annotation>
                  <lang>en</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Content</p>
                </section>
              </body>
            </FictionBook>
            """, annotation);
    }

    private String createFb2WithDate(String date) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>fiction</genre>
                  <author>
                    <first-name>Test</first-name>
                    <last-name>Author</last-name>
                  </author>
                  <book-title>Book with Date</book-title>
                  <date>%s</date>
                  <lang>en</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Content</p>
                </section>
              </body>
            </FictionBook>
            """, date);
    }

    private String createFb2WithDateValue(String dateValue, String dateText) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>fiction</genre>
                  <author>
                    <first-name>Test</first-name>
                    <last-name>Author</last-name>
                  </author>
                  <book-title>Book with Date Value</book-title>
                  <date value="%s">%s</date>
                  <lang>en</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Content</p>
                </section>
              </body>
            </FictionBook>
            """, dateValue, dateText);
    }

    private String createFb2WithSequence(String seriesName, String seriesNumber) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>fiction</genre>
                  <author>
                    <first-name>Test</first-name>
                    <last-name>Author</last-name>
                  </author>
                  <book-title>Book in Series</book-title>
                  <sequence name="%s" number="%s"/>
                  <lang>en</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Content</p>
                </section>
              </body>
            </FictionBook>
            """, seriesName, seriesNumber);
    }

    private String createFb2WithPublishInfo(String publisher, String year, String isbn) {
        StringBuilder publishInfo = new StringBuilder();
        if (publisher != null) {
            publishInfo.append(String.format("      <publisher>%s</publisher>\n", publisher));
        }
        if (year != null) {
            publishInfo.append(String.format("      <year>%s</year>\n", year));
        }
        if (isbn != null) {
            publishInfo.append(String.format("      <isbn>%s</isbn>\n", isbn));
        }

        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>fiction</genre>
                  <author>
                    <first-name>Test</first-name>
                    <last-name>Author</last-name>
                  </author>
                  <book-title>Book with Publish Info</book-title>
                  <lang>en</lang>
                </title-info>
                <publish-info>
            %s    </publish-info>
              </description>
              <body>
                <section>
                  <p>Content</p>
                </section>
              </body>
            </FictionBook>
            """, publishInfo);
    }

    private String createFb2WithKeywords(String keywords) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>fiction</genre>
                  <author>
                    <first-name>Test</first-name>
                    <last-name>Author</last-name>
                  </author>
                  <book-title>Book with Keywords</book-title>
                  <keywords>%s</keywords>
                  <lang>en</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Content</p>
                </section>
              </body>
            </FictionBook>
            """, keywords);
    }

    private String createFb2WithAuthorNames(String firstName, String middleName, String lastName, String nickname) {
        StringBuilder authorInfo = new StringBuilder();
        if (firstName != null) {
            authorInfo.append(String.format("    <first-name>%s</first-name>\n", firstName));
        }
        if (middleName != null) {
            authorInfo.append(String.format("    <middle-name>%s</middle-name>\n", middleName));
        }
        if (lastName != null) {
            authorInfo.append(String.format("    <last-name>%s</last-name>\n", lastName));
        }
        if (nickname != null) {
            authorInfo.append(String.format("    <nickname>%s</nickname>\n", nickname));
        }

        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>fiction</genre>
                  <author>
            %s      </author>
                  <book-title>Book with Complex Author</book-title>
                  <lang>en</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Content</p>
                </section>
              </body>
            </FictionBook>
            """, authorInfo);
    }

    private String createFb2WithCover(byte[] imageData) {
        String base64Image = Base64.getEncoder().encodeToString(imageData);
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:xlink="http://www.w3.org/1999/xlink">
              <description>
                <title-info>
                  <genre>fiction</genre>
                  <author>
                    <first-name>Test</first-name>
                    <last-name>Author</last-name>
                  </author>
                  <book-title>Book with Cover</book-title>
                  <coverpage>
                    <image xlink:href="#cover.jpg"/>
                  </coverpage>
                  <lang>en</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Content</p>
                </section>
              </body>
              <binary id="cover.jpg" content-type="image/jpeg">%s</binary>
            </FictionBook>
            """, base64Image);
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

    private File createFb2File(String content) throws IOException {
        File fb2File = tempDir.resolve("test-" + System.nanoTime() + ".fb2").toFile();
        try (FileOutputStream fos = new FileOutputStream(fb2File)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return fb2File;
    }
}
