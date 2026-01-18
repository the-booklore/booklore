package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.IncompleteSeriesDto;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncompleteSeriesServiceTest {

    @Mock
    private BookMetadataRepository bookMetadataRepository;

    @InjectMocks
    private IncompleteSeriesService incompleteSeriesService;

    private BookMetadataEntity createBook(Long id, String series, Float seriesNumber) {
        BookMetadataEntity book = new BookMetadataEntity();
        book.setBookId(id);
        book.setSeriesName(series);
        book.setSeriesNumber(seriesNumber);
        return book;
    }

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(bookMetadataRepository);
    }

    @Test
    void findIncompleteSeries_withNoSeries_returnsEmptyList() {
        // Arrange
        when(bookMetadataRepository.findAllDistinctSeriesNames()).thenReturn(Collections.emptyList());

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertTrue(result.isEmpty());
        verify(bookMetadataRepository).findAllDistinctSeriesNames();
        verify(bookMetadataRepository, never()).findBooksInSeriesByName(anyString());
    }

    @Test
    void findIncompleteSeries_withCompleteSeries_returnsEmptyList() {
        // Arrange
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(List.of("Complete Series"));
        when(bookMetadataRepository.findBooksInSeriesByName("Complete Series"))
                .thenReturn(Arrays.asList(
                        createBook(1L, "Complete Series", 1.0f),
                        createBook(2L, "Complete Series", 2.0f),
                        createBook(3L, "Complete Series", 3.0f)
                ));

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void findIncompleteSeries_withMissingMiddleBook_returnsIncompleteSeries() {
        // Arrange
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(List.of("Incomplete Series"));
        when(bookMetadataRepository.findBooksInSeriesByName("Incomplete Series"))
                .thenReturn(Arrays.asList(
                        createBook(1L, "Incomplete Series", 1.0f),
                        createBook(2L, "Incomplete Series", 2.0f),
                        createBook(3L, "Incomplete Series", 5.0f) // Missing 3 and 4
                ));

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertEquals(1, result.size());
        IncompleteSeriesDto dto = result.get(0);
        assertEquals("Incomplete Series", dto.getSeriesName());
        assertEquals(5.0f, dto.getMaxSeriesNumber());
        assertEquals(3, dto.getTotalBooks());
        assertEquals(Arrays.asList(1.0f, 2.0f, 5.0f), dto.getPresentNumbers());
        assertEquals(Arrays.asList(3.0f, 4.0f), dto.getMissingNumbers());
    }

    @Test
    void findIncompleteSeries_withMissingFirstBook_returnsIncompleteSeries() {
        // Arrange
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(List.of("Missing First"));
        when(bookMetadataRepository.findBooksInSeriesByName("Missing First"))
                .thenReturn(Arrays.asList(
                        createBook(1L, "Missing First", 2.0f),
                        createBook(2L, "Missing First", 3.0f),
                        createBook(3L, "Missing First", 4.0f)
                ));

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertEquals(1, result.size());
        IncompleteSeriesDto dto = result.get(0);
        assertEquals("Missing First", dto.getSeriesName());
        assertEquals(4.0f, dto.getMaxSeriesNumber());
        assertEquals(List.of(1.0f), dto.getMissingNumbers());
    }

    @Test
    void findIncompleteSeries_withDecimalNumbers_handlesProperly() {
        // Arrange: Series with decimal numbers like 1, 1.5, 2, 3 (complete with decimal variant)
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(List.of("Decimal Series"));
        when(bookMetadataRepository.findBooksInSeriesByName("Decimal Series"))
                .thenReturn(Arrays.asList(
                        createBook(1L, "Decimal Series", 1.0f),
                        createBook(2L, "Decimal Series", 1.5f),
                        createBook(3L, "Decimal Series", 2.0f),
                        createBook(4L, "Decimal Series", 3.0f)
                ));

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertTrue(result.isEmpty(), "Series with decimal variants should be considered complete");
    }

    @Test
    void findIncompleteSeries_withDecimalNumbers_missingPosition_returnsIncompleteSeries() {
        // Arrange: Series with 1, 1.5, 3 (missing position 2)
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(List.of("Decimal Incomplete"));
        when(bookMetadataRepository.findBooksInSeriesByName("Decimal Incomplete"))
                .thenReturn(Arrays.asList(
                        createBook(1L, "Decimal Incomplete", 1.0f),
                        createBook(2L, "Decimal Incomplete", 1.5f),
                        createBook(3L, "Decimal Incomplete", 3.0f) // Missing position 2
                ));

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertEquals(1, result.size());
        IncompleteSeriesDto dto = result.get(0);
        assertEquals("Decimal Incomplete", dto.getSeriesName());
        assertEquals(3.0f, dto.getMaxSeriesNumber());
        assertEquals(List.of(2.0f), dto.getMissingNumbers());
    }

    @Test
    void findIncompleteSeries_withDecimalNumbers_onlyDecimals_missingInteger() {
        // Arrange: Series with only 1.22, 2.5 (missing integer positions 1 and 2)
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(List.of("Only Decimals"));
        when(bookMetadataRepository.findBooksInSeriesByName("Only Decimals"))
                .thenReturn(Arrays.asList(
                        createBook(1L, "Only Decimals", 1.22f),
                        createBook(2L, "Only Decimals", 2.5f)
                ));

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertTrue(result.isEmpty(), "Decimal variants should satisfy integer positions");
    }

    @Test
    void findIncompleteSeries_withMultipleSeries_returnsBothIncomplete() {
        // Arrange
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(Arrays.asList("Series A", "Series B", "Complete Series"));
        
        when(bookMetadataRepository.findBooksInSeriesByName("Series A"))
                .thenReturn(Arrays.asList(
                        createBook(1L, "Series A", 1.0f),
                        createBook(2L, "Series A", 3.0f)
                ));
        
        when(bookMetadataRepository.findBooksInSeriesByName("Series B"))
                .thenReturn(Arrays.asList(
                        createBook(3L, "Series B", 1.0f),
                        createBook(4L, "Series B", 4.0f)
                ));
        
        when(bookMetadataRepository.findBooksInSeriesByName("Complete Series"))
                .thenReturn(Arrays.asList(
                        createBook(5L, "Complete Series", 1.0f),
                        createBook(6L, "Complete Series", 2.0f)
                ));

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getSeriesName().equals("Series A")));
        assertTrue(result.stream().anyMatch(s -> s.getSeriesName().equals("Series B")));
        assertFalse(result.stream().anyMatch(s -> s.getSeriesName().equals("Complete Series")));
    }

    @Test
    void findIncompleteSeries_withNullSeriesNumbers_skipsBooks() {
        // Arrange
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(List.of("Series With Nulls"));
        
        BookMetadataEntity bookWithNull = new BookMetadataEntity();
        bookWithNull.setBookId(1L);
        bookWithNull.setSeriesName("Series With Nulls");
        bookWithNull.setSeriesNumber(null);
        
        when(bookMetadataRepository.findBooksInSeriesByName("Series With Nulls"))
                .thenReturn(Arrays.asList(
                        bookWithNull,
                        createBook(2L, "Series With Nulls", 1.0f),
                        createBook(3L, "Series With Nulls", 3.0f)
                ));

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertEquals(1, result.size());
        IncompleteSeriesDto dto = result.get(0);
        // Should only count books with non-null series numbers
        assertEquals(Arrays.asList(1.0f, 3.0f), dto.getPresentNumbers());
        assertEquals(List.of(2.0f), dto.getMissingNumbers());
    }

    @Test
    void findIncompleteSeries_withEmptyBooks_skipsSeriesnoContent() {
        // Arrange
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(List.of("Empty Series"));
        when(bookMetadataRepository.findBooksInSeriesByName("Empty Series"))
                .thenReturn(Collections.emptyList());

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void findIncompleteSeries_withSingleBook_returnsEmpty() {
        // Arrange: Single book at position 5 would show missing 1-4, but that's expected
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(List.of("Single Book"));
        when(bookMetadataRepository.findBooksInSeriesByName("Single Book"))
                .thenReturn(List.of(createBook(1L, "Single Book", 5.0f)));

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertEquals(1, result.size());
        IncompleteSeriesDto dto = result.get(0);
        assertEquals(Arrays.asList(1.0f, 2.0f, 3.0f, 4.0f), dto.getMissingNumbers());
    }

    @Test
    void getIncompleteSeriesNames_returnsOnlyNames() {
        // Arrange
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(Arrays.asList("Incomplete A", "Incomplete B"));
        
        when(bookMetadataRepository.findBooksInSeriesByName("Incomplete A"))
                .thenReturn(Arrays.asList(
                        createBook(1L, "Incomplete A", 1.0f),
                        createBook(2L, "Incomplete A", 3.0f)
                ));
        
        when(bookMetadataRepository.findBooksInSeriesByName("Incomplete B"))
                .thenReturn(Arrays.asList(
                        createBook(3L, "Incomplete B", 2.0f),
                        createBook(4L, "Incomplete B", 4.0f)
                ));

        // Act
        List<String> result = incompleteSeriesService.getIncompleteSeriesNames();

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.contains("Incomplete A"));
        assertTrue(result.contains("Incomplete B"));
    }

    @Test
    void getIncompleteSeriesNames_withNoIncompleteSeries_returnsEmptyList() {
        // Arrange
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(List.of("Complete Series"));
        when(bookMetadataRepository.findBooksInSeriesByName("Complete Series"))
                .thenReturn(Arrays.asList(
                        createBook(1L, "Complete Series", 1.0f),
                        createBook(2L, "Complete Series", 2.0f)
                ));

        // Act
        List<String> result = incompleteSeriesService.getIncompleteSeriesNames();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void findIncompleteSeries_withDuplicateSeriesNumbers_handlesProperly() {
        // Arrange: Series with duplicate numbers (e.g., two books with series number 1)
        when(bookMetadataRepository.findAllDistinctSeriesNames())
                .thenReturn(List.of("Duplicate Numbers"));
        when(bookMetadataRepository.findBooksInSeriesByName("Duplicate Numbers"))
                .thenReturn(Arrays.asList(
                        createBook(1L, "Duplicate Numbers", 1.0f),
                        createBook(2L, "Duplicate Numbers", 1.0f), // Duplicate
                        createBook(3L, "Duplicate Numbers", 2.0f),
                        createBook(4L, "Duplicate Numbers", 4.0f)
                ));

        // Act
        List<IncompleteSeriesDto> result = incompleteSeriesService.findIncompleteSeries();

        // Assert
        assertEquals(1, result.size());
        IncompleteSeriesDto dto = result.get(0);
        // Present numbers should be distinct
        assertEquals(Arrays.asList(1.0f, 2.0f, 4.0f), dto.getPresentNumbers());
        assertEquals(List.of(3.0f), dto.getMissingNumbers());
    }
}
