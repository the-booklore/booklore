package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FilenamePatternExtractorIntegrationTest {

    @Mock
    private BookdropMetadataHelper metadataHelper;

    @Test
    void extractFromFilename_shouldHandleDateFormatsCorrectlyAfterBugFix() {
        FilenamePatternExtractor extractor = new FilenamePatternExtractor(null, metadataHelper);

        String filename = "BookTitle_25122023.pdf";
        String pattern = "{Title}_{Published:ddMMyyyy}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("BookTitle", result.getTitle());
        assertNotNull(result.getPublishedDate());
        assertEquals(2023, result.getPublishedDate().getYear());
        assertEquals(12, result.getPublishedDate().getMonthValue());
        assertEquals(25, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_shouldHandleMixedDateFormats() {
        FilenamePatternExtractor extractor = new FilenamePatternExtractor(null, metadataHelper);

        String filename = "MyBook_12/5/2023.epub";
        String pattern = "{Title}_{Published:M/d/yyyy}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("MyBook", result.getTitle());
        assertNotNull(result.getPublishedDate());
        assertEquals(2023, result.getPublishedDate().getYear());
        assertEquals(12, result.getPublishedDate().getMonthValue());
        assertEquals(5, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_shouldHandleYearMonthDayFormat() {
        FilenamePatternExtractor extractor = new FilenamePatternExtractor(null, metadataHelper);

        String filename = "Title_2023312.pdf";  // March 12, 2023
        String pattern = "{Title}_{Published:yyyyMd}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Title", result.getTitle());
        assertNotNull(result.getPublishedDate());
        assertEquals(2023, result.getPublishedDate().getYear());
    }

    @Test
    void extractFromFilename_shouldHandleComplexDatePatterns() {
        FilenamePatternExtractor extractor = new FilenamePatternExtractor(null, metadataHelper);

        String filename = "Series_Book2_2023-12-25.txt";
        String pattern = "{Title}_{Published:yyyy-MM-dd}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertTrue(result.getTitle().contains("Series"));
        assertTrue(result.getTitle().contains("Book2"));
        assertNotNull(result.getPublishedDate());
        assertEquals(2023, result.getPublishedDate().getYear());
        assertEquals(12, result.getPublishedDate().getMonthValue());
        assertEquals(25, result.getPublishedDate().getDayOfMonth());
    }
}