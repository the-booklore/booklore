package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ExtractFromComicInfoXmlTest {

    private CbxMetadataExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new CbxMetadataExtractor();
    }

    @Test
    void testExtractFromComicInfoXml_ValidXml_AllFields() throws IOException {
        String xml = """
                <?xml version="1.0"?>
                <ComicInfo>
                    <Title>Batman: The Dark Knight Returns</Title>
                    <Series>Batman: The Dark Knight Returns</Series>
                    <Number>1</Number>
                    <Count>4</Count>
                    <Volume>1</Volume>
                    <Year>1986</Year>
                    <Month>3</Month>
                    <Day>15</Day>
                    <Publisher>DC Comics</Publisher>
                    <Summary>Batman comes out of retirement to fight crime in Gotham City.</Summary>
                    <Writer>Frank Miller</Writer>
                    <Genre>Superhero, Crime</Genre>
                    <Tags>Batman, DC, Frank Miller</Tags>
                    <PageCount>48</PageCount>
                    <LanguageISO>en</LanguageISO>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("valid_all_fields.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("Batman: The Dark Knight Returns", metadata.getTitle());
        assertEquals("Batman: The Dark Knight Returns", metadata.getSeriesName());
        assertEquals(1.0f, metadata.getSeriesNumber());
        assertEquals(4, metadata.getSeriesTotal());
        assertEquals(LocalDate.of(1986, 3, 15), metadata.getPublishedDate());
        assertEquals("DC Comics", metadata.getPublisher());
        assertEquals("Batman comes out of retirement to fight crime in Gotham City.", metadata.getDescription());
        assertEquals(48, metadata.getPageCount());
        assertEquals("en", metadata.getLanguage());

        // Verify authors
        assertTrue(metadata.getAuthors().contains("Frank Miller"));

        // Verify categories
        assertTrue(metadata.getCategories().contains("Superhero"));
        assertTrue(metadata.getCategories().contains("Crime"));
        assertTrue(metadata.getCategories().contains("Batman"));
        assertTrue(metadata.getCategories().contains("DC"));
        assertTrue(metadata.getCategories().contains("Frank Miller"));
    }

    @Test
    void testExtractFromComicInfoXml_MinimalXml() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Simple Comic</Title>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("minimal.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("Simple Comic", metadata.getTitle());
        assertNull(metadata.getSeriesName());
        assertNull(metadata.getSeriesNumber());
        assertNull(metadata.getSeriesTotal());
        assertNull(metadata.getPublishedDate());
        assertNull(metadata.getPublisher());
        assertNull(metadata.getDescription());
        assertNull(metadata.getPageCount());
        assertNull(metadata.getLanguage());
    }

    @Test
    void testExtractFromComicInfoXml_NoTitle_UsesFallback() throws IOException {
        String xml = """
                <ComicInfo>
                    <Series>Mystery Series</Series>
                    <Number>5</Number>
                </ComicInfo>
                """;

        Path parentDir = tempDir.resolve("comic_folder");
        Files.createDirectories(parentDir);
        File xmlFile = createXmlFileInDir(parentDir, "no_title.xml", xml);

        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("comic_folder", metadata.getTitle());
        assertEquals("Mystery Series", metadata.getSeriesName());
        assertEquals(5.0f, metadata.getSeriesNumber());
    }

    @Test
    void testExtractFromComicInfoXml_EmptyTitle_UsesFallback() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title></Title>
                    <Series>Empty Title Series</Series>
                </ComicInfo>
                """;

        Path parentDir = tempDir.resolve("empty_title_comic");
        Files.createDirectories(parentDir);
        File xmlFile = createXmlFileInDir(parentDir, "empty_title.xml", xml);

        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("empty_title_comic", metadata.getTitle());
    }

    @Test
    void testExtractFromComicInfoXml_WhitespaceTitle_UsesFallback() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>   </Title>
                    <Series>Whitespace Title Series</Series>
                </ComicInfo>
                """;

        Path parentDir = tempDir.resolve("whitespace_comic");
        Files.createDirectories(parentDir);
        File xmlFile = createXmlFileInDir(parentDir, "whitespace_title.xml", xml);

        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("whitespace_comic", metadata.getTitle());
    }

    @Test
    void testExtractFromComicInfoXml_SeriesWithoutVolume() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Series>Test Series</Series>
                    <Number>10</Number>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("series_no_volume.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("Test Series", metadata.getSeriesName());
        assertEquals(10.0f, metadata.getSeriesNumber());
    }

    @Test
    void testExtractFromComicInfoXml_SeriesWithVolume() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Series>Test Series</Series>
                    <Volume>2</Volume>
                    <Number>10</Number>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("series_with_volume.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("Test Series", metadata.getSeriesName());
        assertEquals(10.0f, metadata.getSeriesNumber());
    }

    @Test
    void testExtractFromComicInfoXml_SeriesWithEmptyVolume() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Series>Test Series</Series>
                    <Volume></Volume>
                    <Number>10</Number>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("series_empty_volume.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("Test Series", metadata.getSeriesName());
    }

    @Test
    void testExtractFromComicInfoXml_FloatingPointNumber() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Series>Test Series</Series>
                    <Number>10.5</Number>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("floating_number.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals(10.5f, metadata.getSeriesNumber());
    }

    @Test
    void testExtractFromComicInfoXml_InvalidNumberFormat() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Series>Test Series</Series>
                    <Number>not-a-number</Number>
                    <Count>also-not-a-number</Count>
                    <PageCount>invalid</PageCount>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("invalid_numbers.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertNull(metadata.getSeriesNumber());
        assertNull(metadata.getSeriesTotal());
        assertNull(metadata.getPageCount());
    }

    @Test
    void testExtractFromComicInfoXml_DateWithYearOnly() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Year>2020</Year>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("date_year_only.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals(LocalDate.of(2020, 1, 1), metadata.getPublishedDate());
    }

    @Test
    void testExtractFromComicInfoXml_DateWithYearAndMonth() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Year>2020</Year>
                    <Month>6</Month>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("date_year_month.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals(LocalDate.of(2020, 6, 1), metadata.getPublishedDate());
    }

    @Test
    void testExtractFromComicInfoXml_InvalidDate() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Year>2020</Year>
                    <Month>13</Month>
                    <Day>32</Day>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("invalid_date.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertNull(metadata.getPublishedDate());
    }

    @Test
    void testExtractFromComicInfoXml_MultipleAuthors_CommaSeparated() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Writer>John Doe, Jane Smith</Writer>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("multiple_authors_comma.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertTrue(metadata.getAuthors().contains("John Doe"));
        assertTrue(metadata.getAuthors().contains("Jane Smith"));
    }

    @Test
    void testExtractFromComicInfoXml_MultipleAuthors_SemicolonSeparated() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Writer>John Doe; Jane Smith</Writer>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("multiple_authors_semicolon.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertTrue(metadata.getAuthors().contains("John Doe"));
        assertTrue(metadata.getAuthors().contains("Jane Smith"));
    }

    @Test
    void testExtractFromComicInfoXml_MultipleCategories() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Genre>Action, Adventure, Sci-Fi</Genre>
                    <Tags>robots; space; future</Tags>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("multiple_categories.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertTrue(metadata.getCategories().contains("Action"));
        assertTrue(metadata.getCategories().contains("Adventure"));
        assertTrue(metadata.getCategories().contains("Sci-Fi"));
        assertTrue(metadata.getCategories().contains("robots"));
        assertTrue(metadata.getCategories().contains("space"));
        assertTrue(metadata.getCategories().contains("future"));
    }

    @Test
    void testExtractFromComicInfoXml_DescriptionFromSummary() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Summary>This is a summary</Summary>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("description_summary.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("This is a summary", metadata.getDescription());
    }

    @Test
    void testExtractFromComicInfoXml_DescriptionFromDescription() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Description>This is a description</Description>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("description_description.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("This is a description", metadata.getDescription());
    }

    @Test
    void testExtractFromComicInfoXml_SummaryPreferredOverDescription() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Summary>Summary text</Summary>
                    <Description>Description text</Description>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("summary_and_description.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("Summary text", metadata.getDescription());
    }

    @Test
    void testExtractFromComicInfoXml_PageCountFromPageCount() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <PageCount>32</PageCount>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("pagecount_field.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals(32, metadata.getPageCount());
    }

    @Test
    void testExtractFromComicInfoXml_PageCountFromPages() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Pages>48</Pages>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("pages_field.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals(48, metadata.getPageCount());
    }

    @Test
    void testExtractFromComicInfoXml_SpecialCharactersInFields() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test &amp; Verify: "Special" Characters</Title>
                    <Series>Series &lt;Test&gt;</Series>
                    <Writer>O'Brien &amp; Associates</Writer>
                    <Summary>A story with &quot;quotes&quot; and &lt;tags&gt;</Summary>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("special_characters.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("Test & Verify: \"Special\" Characters", metadata.getTitle());
        assertEquals("Series <Test>", metadata.getSeriesName());
        assertTrue(metadata.getAuthors().contains("O'Brien & Associates"));
        assertEquals("A story with \"quotes\" and <tags>", metadata.getDescription());
    }

    @Test
    void testExtractFromComicInfoXml_MalformedXml_ReturnsFallbackMetadata() throws IOException {
        String xml = "<ComicInfo><Title>Unclosed Tag";

        File xmlFile = createXmlFile("malformed.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        // Should return fallback title (parent directory name or file name)
        assertNotNull(metadata.getTitle());
    }

    @Test
    void testExtractFromComicInfoXml_EmptyXml_ReturnsFallbackMetadata() throws IOException {
        String xml = "";

        File xmlFile = createXmlFile("empty.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertNotNull(metadata.getTitle());
    }

    @Test
    void testExtractFromComicInfoXml_InvalidXmlContent_ReturnsFallbackMetadata() throws IOException {
        String xml = "This is not XML at all!";

        File xmlFile = createXmlFile("not_xml.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertNotNull(metadata.getTitle());
    }

    @Test
    void testExtractFromComicInfoXml_XmlWithNamespace() throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ComicInfo xmlns="http://example.com/schema">
                    <Title>Namespaced Comic</Title>
                    <Series>Namespace Series</Series>
                    <Number>1</Number>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("with_namespace.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        // With namespace awareness enabled, should extract correctly
        assertNotNull(metadata);
        assertEquals("Namespaced Comic", metadata.getTitle());
        assertEquals("Namespace Series", metadata.getSeriesName());
        assertEquals(1.0f, metadata.getSeriesNumber());
    }

    @Test
    void testExtractFromComicInfoXml_XXEAttackPrevention() throws IOException {
        String xxeXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <ComicInfo>
                    <Title>&xxe;</Title>
                </ComicInfo>
                """;

        Path parentDir = tempDir.resolve("xxe_attack");
        File xmlFile = new File(parentDir.toFile(), "ComicInfo.xml");
        Files.createDirectories(parentDir);
        Files.writeString(xmlFile.toPath(), xxeXml, StandardCharsets.UTF_8);

        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        // XXE protection should prevent parsing and return fallback metadata
        assertNotNull(metadata);
        assertEquals("xxe_attack", metadata.getTitle());  // Fallback to filename
        // Should not contain any resolved entity content
        assertFalse(metadata.getTitle().contains("&xxe;"));
        assertFalse(metadata.getTitle().contains("root:"));
    }

    @Test
    void testExtractFromComicInfoXml_BillionLaughsAttackPrevention() throws IOException {
        String billionLaughsXml = """
                <?xml version="1.0"?>
                <!DOCTYPE lolz [
                  <!ENTITY lol "lol">
                  <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
                  <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
                ]>
                <ComicInfo>
                    <Title>&lol3;</Title>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("billion_laughs.xml", billionLaughsXml);

        // Should fail fast, not hang or consume excessive memory
        assertDoesNotThrow(() -> {
            BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);
            assertNotNull(metadata);
        });
    }

    @Test
    void testExtractFromComicInfoXml_NegativeNumbers() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test</Title>
                    <Number>-5</Number>
                    <Count>-10</Count>
                    <PageCount>-32</PageCount>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("negative_numbers.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        // Document behavior: negative numbers are parsed but may not be meaningful
        assertNotNull(metadata);
        assertEquals(-5.0f, metadata.getSeriesNumber());
        assertEquals(-10, metadata.getSeriesTotal());
        assertEquals(-32, metadata.getPageCount());
    }

    @Test
    void testExtractFromComicInfoXml_VeryLargeNumbers() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test</Title>
                    <Number>999999999999999</Number>
                    <PageCount>2147483648</PageCount>  <!-- Integer.MAX_VALUE + 1, should overflow -->
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("large_numbers.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertNotNull(metadata.getSeriesNumber());
        // PageCount overflows Integer.MAX_VALUE, parseInteger returns null
        assertNull(metadata.getPageCount());
    }

    @Test
    void testExtractFromComicInfoXml_EmptyXmlFile() throws IOException {
        Path parentDir = tempDir.resolve("empty");
        Files.createDirectories(parentDir);
        File xmlFile = new File(parentDir.toFile(), "ComicInfo.xml");
        Files.writeString(xmlFile.toPath(), "", StandardCharsets.UTF_8);

        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        // Should return fallback metadata, not crash
        assertNotNull(metadata);
        assertEquals("empty", metadata.getTitle());
    }

    @Test
    void testExtractFromComicInfoXml_VeryLongStrings() throws IOException {
        String longTitle = "A".repeat(10000);
        String longDescription = "B".repeat(10000);

        String xml = String.format("""
                <ComicInfo>
                    <Title>%s</Title>
                    <Summary>%s</Summary>
                </ComicInfo>
                """, longTitle, longDescription);

        File xmlFile = createXmlFile("long_strings.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals(longTitle, metadata.getTitle());
        assertEquals(longDescription, metadata.getDescription());
    }

    @Test
    void testExtractFromComicInfoXml_FileDoesNotExist_ReturnsFallbackMetadata() {
        Path parentDir = tempDir.resolve("does_not_exist");
        File nonExistentFile = new File(parentDir.toFile(), "ComicInfo.xml");

        BookMetadata metadata = extractor.extractFromComicInfoXml(nonExistentFile);

        assertNotNull(metadata);
        assertEquals("does_not_exist", metadata.getTitle());
    }

    @ParameterizedTest
    @CsvSource({
            "2020,,,2020-01-01",
            "2020,6,,2020-06-01",
            "2020,3,15,2020-03-15",
            "2020,13,32,"  // Invalid
    })
    void testExtractFromComicInfoXml_DateParsing(String year, String month, String day, String expected) throws IOException {
        String xml = String.format("""
                        <ComicInfo>
                            <Title>Date Test</Title>
                            %s%s%s
                        </ComicInfo>
                        """,
                year != null ? "<Year>" + year + "</Year>" : "",
                month != null ? "<Month>" + month + "</Month>" : "",
                day != null ? "<Day>" + day + "</Day>" : ""
        );

        File xmlFile = createXmlFile("date_test.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        if (expected != null && !expected.isBlank()) {
            assertEquals(LocalDate.parse(expected), metadata.getPublishedDate());
        } else {
            assertNull(metadata.getPublishedDate());
        }
    }

    @Test
    void testExtractFromComicInfoXml_NullInput_ThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extractFromComicInfoXml(null)
        );
        assertEquals("XML file cannot be null", exception.getMessage());
    }

    @Test
    void testExtractFromComicInfoXml_CompleteMetadata_AllFieldsPopulated() throws IOException {
        String xml = """
                <?xml version="1.0"?>
                <ComicInfo>
                    <Title>Batman: The Dark Knight Returns</Title>
                    <Series>Batman: The Dark Knight Returns</Series>
                    <Number>1</Number>
                    <Count>4</Count>
                    <Volume>1</Volume>
                    <Year>1986</Year>
                    <Month>3</Month>
                    <Day>15</Day>
                    <Publisher>DC Comics</Publisher>
                    <Summary>Batman comes out of retirement to fight crime in Gotham City.</Summary>
                    <Writer>Frank Miller</Writer>
                    <Penciller>Frank Miller</Penciller>
                    <Inker>Klaus Janson</Inker>
                    <Colorist>Lynn Varley</Colorist>
                    <Letterer>John Costanza</Letterer>
                    <CoverArtist>Frank Miller</CoverArtist>
                    <Genre>Superhero, Crime</Genre>
                    <Tags>Batman, DC, Frank Miller</Tags>
                    <PageCount>48</PageCount>
                    <LanguageISO>en</LanguageISO>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("complete_metadata.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("Batman: The Dark Knight Returns", metadata.getTitle());
        assertEquals("Batman: The Dark Knight Returns", metadata.getSeriesName());
        assertEquals(1.0f, metadata.getSeriesNumber());
        assertEquals(4, metadata.getSeriesTotal());
        assertEquals(LocalDate.of(1986, 3, 15), metadata.getPublishedDate());
        assertEquals("DC Comics", metadata.getPublisher());
        assertEquals("Batman comes out of retirement to fight crime in Gotham City.", metadata.getDescription());
        assertEquals(48, metadata.getPageCount());
        assertEquals("en", metadata.getLanguage());

        // Verify authors
        assertTrue(metadata.getAuthors().contains("Frank Miller"));

        // Verify categories
        assertTrue(metadata.getCategories().contains("Superhero"));
        assertTrue(metadata.getCategories().contains("Crime"));
        assertTrue(metadata.getCategories().contains("Batman"));
        assertTrue(metadata.getCategories().contains("DC"));
        assertTrue(metadata.getCategories().contains("Frank Miller"));
    }

    @Test
    void testExtractFromComicInfoXml_XmlWithComments() throws IOException {
        String xml = """
                <ComicInfo>
                    <!-- This is a comment -->
                    <Title>Comic With Comments</Title>
                    <!-- Another comment -->
                    <Series>Comment Series</Series>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("with_comments.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("Comic With Comments", metadata.getTitle());
        assertEquals("Comment Series", metadata.getSeriesName());
    }

    @Test
    void testExtractFromComicInfoXml_LeadingAndTrailingWhitespace() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>  Trimmed Title  </Title>
                    <Series>  Trimmed Series  </Series>
                    <Writer>  Writer Name  </Writer>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("with_whitespace.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("Trimmed Title", metadata.getTitle());
        assertEquals("Trimmed Series", metadata.getSeriesName());
        assertTrue(metadata.getAuthors().contains("Writer Name"));
    }

    @Test
    void testExtractFromComicInfoXml_DuplicateAuthors_RemovedBySet() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Writer>Frank Miller</Writer>
                    <Penciller>Frank Miller</Penciller>
                    <CoverArtist>Frank Miller</CoverArtist>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("duplicate_authors.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals(1, metadata.getAuthors().size());
        assertTrue(metadata.getAuthors().contains("Frank Miller"));
    }

    @Test
    void testExtractFromComicInfoXml_NoAuthors_AuthorsNull() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Series>Test Series</Series>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("no_authors.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        // If no authors, the set should be null or empty
        assertTrue(metadata.getAuthors() == null || metadata.getAuthors().isEmpty());
    }

    @Test
    void testExtractFromComicInfoXml_NoCategories_CategoriesNull() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test Comic</Title>
                    <Series>Test Series</Series>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("no_categories.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertTrue(metadata.getCategories() == null || metadata.getCategories().isEmpty());
    }

    @Test
    void testExtractFromComicInfoXml_FileInRootDirectory() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Root Comic</Title>
                </ComicInfo>
                """;

        // Create file directly in tempDir (no parent directory)
        File xmlFile = createXmlFile("root_comic.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals("Root Comic", metadata.getTitle());
    }

    @Test
    void testExtractFromComicInfoXml_ZeroValues() throws IOException {
        String xml = """
                <ComicInfo>
                    <Title>Test</Title>
                    <Number>0</Number>
                    <Count>0</Count>
                    <PageCount>0</PageCount>
                </ComicInfo>
                """;

        File xmlFile = createXmlFile("zero_values.xml", xml);
        BookMetadata metadata = extractor.extractFromComicInfoXml(xmlFile);

        assertNotNull(metadata);
        assertEquals(0.0f, metadata.getSeriesNumber());
        assertEquals(0, metadata.getSeriesTotal());
        assertEquals(0, metadata.getPageCount());
    }

    // Helper methods

    private File createXmlFile(String filename, String content) throws IOException {
        return createXmlFileInDir(tempDir, filename, content);
    }

    private File createXmlFileInDir(Path dir, String filename, String content) throws IOException {
        Path xmlPath = dir.resolve(filename);
        Files.writeString(xmlPath, content, StandardCharsets.UTF_8);
        return xmlPath.toFile();
    }
}
