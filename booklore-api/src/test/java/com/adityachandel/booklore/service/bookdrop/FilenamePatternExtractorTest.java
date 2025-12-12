package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.BookdropPatternExtractRequest;
import com.adityachandel.booklore.model.dto.response.BookdropPatternExtractResult;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilenamePatternExtractorTest {

    @Mock
    private BookdropFileRepository bookdropFileRepository;

    @InjectMocks
    private FilenamePatternExtractor extractor;

    private BookdropFileEntity createFileEntity(Long id, String fileName) {
        BookdropFileEntity entity = new BookdropFileEntity();
        entity.setId(id);
        entity.setFileName(fileName);
        entity.setFilePath("/bookdrop/" + fileName);
        return entity;
    }

    @Test
    void extractFromFilename_WithSeriesAndChapter_ShouldExtractBoth() {
        String filename = "Chronicles of Earth - Ch 25.cbz";
        String pattern = "{SeriesName} - Ch {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(25.0f, result.getSeriesNumber());
    }

    @Test
    void extractFromFilename_WithVolumeAndIssuePattern_ShouldExtractCorrectly() {
        String filename = "Chronicles of Earth Vol.3 #100.cbz";
        String pattern = "{SeriesName} Vol.{SeriesTotal} #{SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(3, result.getSeriesTotal());
        assertEquals(100.0f, result.getSeriesNumber());
    }

    @Test
    void extractFromFilename_WithYearPattern_ShouldExtractYear() {
        String filename = "Chronicles of Earth (2016) 001.cbz";
        String pattern = "{SeriesName} ({Year}) {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(2016, result.getPublishedDate().getYear());
        assertEquals(1.0f, result.getSeriesNumber());
    }

    @Test
    void extractFromFilename_TitleWithYear_ShouldExtractBoth() {
        String filename = "The Last Kingdom (1965).epub";
        String pattern = "{Title} ({Year})";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("The Last Kingdom", result.getTitle());
        assertEquals(1965, result.getPublishedDate().getYear());
    }

    @Test
    void extractFromFilename_WithAuthorAndTitle_ShouldExtractBoth() {
        String filename = "John Smith - The Lost City.epub";
        String pattern = "{Authors} - {Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals(Set.of("John Smith"), result.getAuthors());
        assertEquals("The Lost City", result.getTitle());
    }

    @Test
    void extractFromFilename_WithMultipleAuthors_ShouldParseAll() {
        String filename = "John Smith, Jane Doe - The Lost City.epub";
        String pattern = "{Authors} - {Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertTrue(result.getAuthors().contains("John Smith"));
        assertTrue(result.getAuthors().contains("Jane Doe"));
        assertEquals("The Lost City", result.getTitle());
    }

    @Test
    void extractFromFilename_WithDecimalSeriesNumber_ShouldParseCorrectly() {
        String filename = "Chronicles of Earth - Ch 10.5.cbz";
        String pattern = "{SeriesName} - Ch {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(10.5f, result.getSeriesNumber());
    }

    @Test
    void extractFromFilename_WhenPatternDoesNotMatch_ShouldReturnNull() {
        String filename = "Random File Name.pdf";
        String pattern = "{SeriesName} - Ch {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNull(result);
    }

    @Test
    void extractFromFilename_WithNullPattern_ShouldReturnNull() {
        String filename = "Test File.pdf";

        BookMetadata result = extractor.extractFromFilename(filename, null);

        assertNull(result);
    }

    @Test
    void extractFromFilename_WithEmptyPattern_ShouldReturnNull() {
        String filename = "Test File.pdf";

        BookMetadata result = extractor.extractFromFilename(filename, "");

        assertNull(result);
    }

    @Test
    void extractFromFilename_WithPublisherYearAndIssue_ShouldExtractAll() {
        String filename = "Epic Press - Chronicles of Earth #001 (2011).cbz";
        String pattern = "{Publisher} - {SeriesName} #{SeriesNumber} ({Year})";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Epic Press", result.getPublisher());
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(1.0f, result.getSeriesNumber());
        assertEquals(2011, result.getPublishedDate().getYear());
    }

    @Test
    void extractFromFilename_WithLanguageTag_ShouldExtractLanguage() {
        String filename = "Chronicles of Earth - Ch 500 [EN].cbz";
        String pattern = "{SeriesName} - Ch {SeriesNumber} [{Language}]";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(500.0f, result.getSeriesNumber());
        assertEquals("EN", result.getLanguage());
    }

    @Test
    void bulkExtract_WithSelectedFiles_ShouldProcessAll() {
        BookdropFileEntity file1 = createFileEntity(1L, "Chronicles A - Ch 1.cbz");
        BookdropFileEntity file2 = createFileEntity(2L, "Chronicles B - Ch 2.cbz");
        BookdropFileEntity file3 = createFileEntity(3L, "Random Name.cbz");

        BookdropPatternExtractRequest request = new BookdropPatternExtractRequest();
        request.setPattern("{SeriesName} - Ch {SeriesNumber}");
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L, 2L, 3L));

        when(bookdropFileRepository.findAllById(anyList())).thenReturn(List.of(file1, file2, file3));

        BookdropPatternExtractResult result = extractor.bulkExtract(request);

        assertNotNull(result);
        assertEquals(3, result.getTotalFiles());
        assertEquals(2, result.getSuccessfullyExtracted());
        assertEquals(1, result.getFailed());

        var successResults = result.getResults().stream()
                .filter(BookdropPatternExtractResult.FileExtractionResult::isSuccess)
                .toList();
        assertEquals(2, successResults.size());
    }

    @Test
    void extractFromFilename_WithSpecialCharacters_ShouldHandleCorrectly() {
        String filename = "Chronicles (Special Edition) - Ch 5.cbz";
        String pattern = "{SeriesName} - Ch {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles (Special Edition)", result.getSeriesName());
        assertEquals(5.0f, result.getSeriesNumber());
    }

    // ===== Greedy Matching Tests =====

    @Test
    void extractFromFilename_SeriesNameOnly_ShouldCaptureFullName() {
        String filename = "Chronicles of Earth.cbz";
        String pattern = "{SeriesName}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
    }

    @Test
    void extractFromFilename_SeriesNameAlone_ShouldCaptureEverything() {
        String filename = "Chronicles of Earth - Ch 01.cbz";
        String pattern = "{SeriesName}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        // Without separators in pattern, captures everything
        assertEquals("Chronicles of Earth - Ch 01", result.getSeriesName());
    }

    @Test
    void extractFromFilename_TitleOnly_ShouldCaptureFullTitle() {
        String filename = "The Last Kingdom.epub";
        String pattern = "{Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("The Last Kingdom", result.getTitle());
    }

    // ===== Complex Pattern Tests =====

    @Test
    void extractFromFilename_SeriesNumberAndTitle_ShouldExtractBoth() {
        String filename = "Chronicles of Earth 01 - The Beginning.epub";
        String pattern = "{SeriesName} {SeriesNumber} - {Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(1.0f, result.getSeriesNumber());
        assertEquals("The Beginning", result.getTitle());
    }

    @Test
    void extractFromFilename_AuthorSeriesTitleFormat_ShouldExtractAll() {
        String filename = "Chronicles of Earth 07 - The Final Battle - John Smith.epub";
        String pattern = "{SeriesName} {SeriesNumber} - {Title} - {Authors}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(7.0f, result.getSeriesNumber());
        assertEquals("The Final Battle", result.getTitle());
        assertEquals(Set.of("John Smith"), result.getAuthors());
    }

    @Test
    void extractFromFilename_AuthorTitleYear_ShouldExtractAll() {
        String filename = "John Smith - The Lost City (1949).epub";
        String pattern = "{Authors} - {Title} ({Year})";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals(Set.of("John Smith"), result.getAuthors());
        assertEquals("The Lost City", result.getTitle());
        assertEquals(1949, result.getPublishedDate().getYear());
    }

    @Test
    void extractFromFilename_AuthorWithCommas_ShouldParseProperly() {
        String filename = "Smith, John R. - The Lost City.epub";
        String pattern = "{Authors} - {Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals(Set.of("Smith", "John R."), result.getAuthors());
        assertEquals("The Lost City", result.getTitle());
    }

    @Test
    void extractFromFilename_PartNumberFormat_ShouldExtractCorrectly() {
        String filename = "Chronicles of Earth - Part 2 - Rising Darkness.epub";
        String pattern = "{SeriesName} - Part {SeriesNumber} - {Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(2.0f, result.getSeriesNumber());
        assertEquals("Rising Darkness", result.getTitle());
    }

    @Test
    void extractFromFilename_PublisherBracketFormat_ShouldExtractCorrectly() {
        String filename = "[Epic Press] Chronicles of Earth Vol.5.epub";
        String pattern = "[{Publisher}] {SeriesName} Vol.{SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Epic Press", result.getPublisher());
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(5.0f, result.getSeriesNumber());
    }

    @Test
    void extractFromFilename_CalibreStyleFormat_ShouldExtractCorrectly() {
        String filename = "Chronicles of Earth 01 The Beginning - John Smith.epub";
        String pattern = "{SeriesName} {SeriesNumber} {Title} - {Authors}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(1.0f, result.getSeriesNumber());
        assertEquals("The Beginning", result.getTitle());
        assertEquals(Set.of("John Smith"), result.getAuthors());
    }
}
