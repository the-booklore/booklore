package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.IncompleteSeriesDto;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class IncompleteSeriesService {

    private final BookMetadataRepository bookMetadataRepository;

    /**
     * Find all series that have missing book numbers.
     * A series is considered incomplete if there are gaps in the series numbers.
     * For example, if a series has books 1, 2, 5 (max is 5), then books 3 and 4 are missing.
     * Handles decimal series numbers like 1.22, 1.5, etc.
     */
    public List<IncompleteSeriesDto> findIncompleteSeries() {
        List<String> seriesNames = bookMetadataRepository.findAllDistinctSeriesNames();
        List<IncompleteSeriesDto> incompleteSeries = new ArrayList<>();

        for (String seriesName : seriesNames) {
            List<BookMetadataEntity> booksInSeries = bookMetadataRepository.findBooksInSeriesByName(seriesName);
            
            if (booksInSeries.isEmpty()) {
                continue;
            }

            List<Float> presentNumbers = booksInSeries.stream()
                    .map(BookMetadataEntity::getSeriesNumber)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            if (presentNumbers.isEmpty()) {
                continue;
            }

            Float maxSeriesNumber = presentNumbers.get(presentNumbers.size() - 1);
            List<Float> missingNumbers = findMissingNumbers(presentNumbers, maxSeriesNumber);

            // Only include series that have missing numbers
            if (!missingNumbers.isEmpty()) {
                incompleteSeries.add(IncompleteSeriesDto.builder()
                        .seriesName(seriesName)
                        .maxSeriesNumber(maxSeriesNumber)
                        .totalBooks(booksInSeries.size())
                        .presentNumbers(presentNumbers)
                        .missingNumbers(missingNumbers)
                        .build());
            }
        }

        return incompleteSeries;
    }

    /**
     * Find missing numbers in a series, considering decimal numbers.
     * For integer series (1, 2, 3), find all missing integers up to max.
     * For decimal series (1, 1.5, 2), find missing integers and known decimal patterns.
     */
    private List<Float> findMissingNumbers(List<Float> presentNumbers, Float maxSeriesNumber) {
        List<Float> missing = new ArrayList<>();
        
        // Check if series uses only integers
        boolean hasDecimals = presentNumbers.stream()
                .anyMatch(num -> num % 1 != 0);

        if (!hasDecimals) {
            // For integer-only series, check all integers from 1 to max
            int maxInt = maxSeriesNumber.intValue();
            for (int i = 1; i <= maxInt; i++) {
                float num = (float) i;
                if (!presentNumbers.contains(num)) {
                    missing.add(num);
                }
            }
        } else {
            // For series with decimals, check integer positions
            int maxInt = (int) Math.floor(maxSeriesNumber);
            for (int i = 1; i <= maxInt; i++) {
                float num = (float) i;
                if (!presentNumbers.contains(num)) {
                    // Check if there are any decimal variants (e.g., 1.1, 1.2, 1.5)
                    final int currentPosition = i;
                    boolean hasDecimalVariant = presentNumbers.stream()
                            .anyMatch(p -> Math.floor(p) == currentPosition && p != currentPosition);
                    
                    // Only mark as missing if there's no decimal variant
                    if (!hasDecimalVariant) {
                        missing.add(num);
                    }
                }
            }
        }

        return missing;
    }

    /**
     * Get list of series names that are incomplete
     */
    public List<String> getIncompleteSeriesNames() {
        return findIncompleteSeries().stream()
                .map(IncompleteSeriesDto::getSeriesName)
                .collect(Collectors.toList());
    }
}
