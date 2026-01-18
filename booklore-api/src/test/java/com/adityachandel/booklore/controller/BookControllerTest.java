package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.IncompleteSeriesDto;
import com.adityachandel.booklore.service.IncompleteSeriesService;
import com.adityachandel.booklore.service.book.BookService;
import com.adityachandel.booklore.service.book.BookUpdateService;
import com.adityachandel.booklore.service.metadata.BookMetadataService;
import com.adityachandel.booklore.service.recommender.BookRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookControllerTest {

    @Mock
    private BookService bookService;

    @Mock
    private BookUpdateService bookUpdateService;

    @Mock
    private BookRecommendationService bookRecommendationService;

    @Mock
    private BookMetadataService bookMetadataService;

    @Mock
    private IncompleteSeriesService incompleteSeriesService;

    @InjectMocks
    private BookController bookController;

    @BeforeEach
    void setUp() {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getIncompleteSeries_returnsListOfIncompleteSeries() {
        // Arrange
        IncompleteSeriesDto series1 = IncompleteSeriesDto.builder()
                .seriesName("Incomplete Series A")
                .maxSeriesNumber(5.0f)
                .totalBooks(3)
                .presentNumbers(Arrays.asList(1.0f, 2.0f, 5.0f))
                .missingNumbers(Arrays.asList(3.0f, 4.0f))
                .build();

        IncompleteSeriesDto series2 = IncompleteSeriesDto.builder()
                .seriesName("Incomplete Series B")
                .maxSeriesNumber(4.0f)
                .totalBooks(2)
                .presentNumbers(Arrays.asList(1.0f, 4.0f))
                .missingNumbers(Arrays.asList(2.0f, 3.0f))
                .build();

        List<IncompleteSeriesDto> expectedSeries = Arrays.asList(series1, series2);
        when(incompleteSeriesService.findIncompleteSeries()).thenReturn(expectedSeries);

        // Act
        ResponseEntity<List<IncompleteSeriesDto>> response = bookController.getIncompleteSeries();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("Incomplete Series A", response.getBody().get(0).getSeriesName());
        assertEquals("Incomplete Series B", response.getBody().get(1).getSeriesName());
        verify(incompleteSeriesService).findIncompleteSeries();
    }

    @Test
    void getIncompleteSeries_whenNoIncompleteSeries_returnsEmptyList() {
        // Arrange
        when(incompleteSeriesService.findIncompleteSeries()).thenReturn(Collections.emptyList());

        // Act
        ResponseEntity<List<IncompleteSeriesDto>> response = bookController.getIncompleteSeries();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verify(incompleteSeriesService).findIncompleteSeries();
    }

    @Test
    void getIncompleteSeries_verifiesCorrectDataStructure() {
        // Arrange
        IncompleteSeriesDto series = IncompleteSeriesDto.builder()
                .seriesName("Test Series")
                .maxSeriesNumber(10.0f)
                .totalBooks(8)
                .presentNumbers(Arrays.asList(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 8.0f, 10.0f))
                .missingNumbers(Arrays.asList(7.0f, 9.0f))
                .build();

        when(incompleteSeriesService.findIncompleteSeries()).thenReturn(List.of(series));

        // Act
        ResponseEntity<List<IncompleteSeriesDto>> response = bookController.getIncompleteSeries();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        
        IncompleteSeriesDto result = response.getBody().get(0);
        assertEquals("Test Series", result.getSeriesName());
        assertEquals(10.0f, result.getMaxSeriesNumber());
        assertEquals(8, result.getTotalBooks());
        assertEquals(8, result.getPresentNumbers().size());
        assertEquals(2, result.getMissingNumbers().size());
        assertTrue(result.getMissingNumbers().contains(7.0f));
        assertTrue(result.getMissingNumbers().contains(9.0f));
    }

    @Test
    void getIncompleteSeriesNames_returnsListOfSeriesNames() {
        // Arrange
        List<String> expectedNames = Arrays.asList(
                "Incomplete Series A",
                "Incomplete Series B",
                "Incomplete Series C"
        );
        when(incompleteSeriesService.getIncompleteSeriesNames()).thenReturn(expectedNames);

        // Act
        ResponseEntity<List<String>> response = bookController.getIncompleteSeriesNames();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
        assertTrue(response.getBody().contains("Incomplete Series A"));
        assertTrue(response.getBody().contains("Incomplete Series B"));
        assertTrue(response.getBody().contains("Incomplete Series C"));
        verify(incompleteSeriesService).getIncompleteSeriesNames();
    }

    @Test
    void getIncompleteSeriesNames_whenNoIncompleteSeries_returnsEmptyList() {
        // Arrange
        when(incompleteSeriesService.getIncompleteSeriesNames()).thenReturn(Collections.emptyList());

        // Act
        ResponseEntity<List<String>> response = bookController.getIncompleteSeriesNames();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verify(incompleteSeriesService).getIncompleteSeriesNames();
    }

    @Test
    void getIncompleteSeriesNames_returnsSingleSeries() {
        // Arrange
        when(incompleteSeriesService.getIncompleteSeriesNames())
                .thenReturn(List.of("Single Incomplete Series"));

        // Act
        ResponseEntity<List<String>> response = bookController.getIncompleteSeriesNames();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Single Incomplete Series", response.getBody().get(0));
    }

    @Test
    void getIncompleteSeries_withDecimalSeriesNumbers() {
        // Arrange
        IncompleteSeriesDto series = IncompleteSeriesDto.builder()
                .seriesName("Decimal Series")
                .maxSeriesNumber(5.0f)
                .totalBooks(4)
                .presentNumbers(Arrays.asList(1.0f, 1.5f, 2.0f, 5.0f))
                .missingNumbers(Arrays.asList(3.0f, 4.0f))
                .build();

        when(incompleteSeriesService.findIncompleteSeries()).thenReturn(List.of(series));

        // Act
        ResponseEntity<List<IncompleteSeriesDto>> response = bookController.getIncompleteSeries();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        
        IncompleteSeriesDto result = response.getBody().get(0);
        assertTrue(result.getPresentNumbers().contains(1.5f));
        assertEquals(4, result.getTotalBooks());
    }
}
