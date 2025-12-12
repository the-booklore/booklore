package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.BookdropPatternExtractRequest;
import com.adityachandel.booklore.model.dto.response.BookdropPatternExtractResult;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilenamePatternExtractor {

    private final BookdropFileRepository bookdropFileRepository;

    private static final Map<String, PlaceholderConfig> PLACEHOLDER_CONFIGS = Map.of(
            "SeriesName", new PlaceholderConfig("(.+?)", "seriesName"),
            "Title", new PlaceholderConfig("(.+?)", "title"),
            "Authors", new PlaceholderConfig("(.+?)", "authors"),
            "SeriesNumber", new PlaceholderConfig("(\\d+(?:\\.\\d+)?)", "seriesNumber"),
            "SeriesBookNumber", new PlaceholderConfig("(\\d+(?:\\.\\d+)?)", "seriesNumber"),
            "Year", new PlaceholderConfig("(\\d{4})", "publishedDate"),
            "Publisher", new PlaceholderConfig("(.+?)", "publisher"),
            "Language", new PlaceholderConfig("([a-zA-Z]+)", "language"),
            "SeriesTotal", new PlaceholderConfig("(\\d+)", "seriesTotal")
    );

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");

    public BookdropPatternExtractResult bulkExtract(BookdropPatternExtractRequest request) {
        List<Long> fileIds = resolveFileIds(request);
        List<BookdropFileEntity> files = bookdropFileRepository.findAllById(fileIds);

        List<BookdropPatternExtractResult.FileExtractionResult> results = new ArrayList<>();
        int successCount = 0;

        for (BookdropFileEntity file : files) {
            BookdropPatternExtractResult.FileExtractionResult result = extractFromFile(file, request.getPattern());
            results.add(result);
            if (result.isSuccess()) {
                successCount++;
            }
        }

        return BookdropPatternExtractResult.builder()
                .totalFiles(files.size())
                .successfullyExtracted(successCount)
                .failed(files.size() - successCount)
                .results(results)
                .build();
    }

    public BookMetadata extractFromFilename(String filename, String pattern) {
        log.info("Extracting from filename='{}' with pattern='{}'", filename, pattern);
        String nameOnly = FilenameUtils.getBaseName(filename);
        log.info("Base name (without extension): '{}'", nameOnly);
        
        ParsedPattern parsedPattern = parsePattern(pattern);

        if (parsedPattern == null) {
            log.warn("Failed to parse pattern: '{}'", pattern);
            return null;
        }

        String regexPattern = parsedPattern.compiledPattern().pattern();
        log.info("Compiled regex pattern: '{}'", regexPattern);
        
        Matcher extractMatcher = parsedPattern.compiledPattern().matcher(nameOnly);
        boolean matches = extractMatcher.matches();
        log.info("Regex match result: {}", matches);
        
        if (!matches) {
            log.warn("Pattern did not match filename");
            return null;
        }

        if (extractMatcher.groupCount() > 0) {
            log.info("Extracted value from group 1: '{}'", extractMatcher.group(1));
        }
        
        return buildMetadataFromMatch(extractMatcher, parsedPattern.placeholderOrder());
    }

    private BookdropPatternExtractResult.FileExtractionResult extractFromFile(
            BookdropFileEntity file, 
            String pattern) {
        try {
            BookMetadata extracted = extractFromFilename(file.getFileName(), pattern);
            
            if (extracted == null) {
                return BookdropPatternExtractResult.FileExtractionResult.builder()
                        .fileId(file.getId())
                        .fileName(file.getFileName())
                        .success(false)
                        .errorMessage("Pattern did not match filename")
                        .build();
            }

            return BookdropPatternExtractResult.FileExtractionResult.builder()
                    .fileId(file.getId())
                    .fileName(file.getFileName())
                    .success(true)
                    .extractedMetadata(extracted)
                    .build();

        } catch (Exception e) {
            log.error("Error extracting metadata from file {}: {}", file.getFileName(), e.getMessage());
            return BookdropPatternExtractResult.FileExtractionResult.builder()
                    .fileId(file.getId())
                    .fileName(file.getFileName())
                    .success(false)
                    .errorMessage("Extraction error: " + e.getMessage())
                    .build();
        }
    }

    private List<Long> resolveFileIds(BookdropPatternExtractRequest request) {
        if (Boolean.TRUE.equals(request.getSelectAll())) {
            List<Long> excludedIds = request.getExcludedIds() != null 
                    ? request.getExcludedIds() 
                    : Collections.emptyList();
            return bookdropFileRepository.findAllExcludingIdsFlat(excludedIds);
        }
        return request.getSelectedIds() != null 
                ? request.getSelectedIds() 
                : Collections.emptyList();
    }

    private ParsedPattern parsePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }

        List<PlaceholderMatch> placeholderMatches = findAllPlaceholders(pattern);
        StringBuilder regexBuilder = new StringBuilder();
        List<String> placeholderOrder = new ArrayList<>();
        int lastEnd = 0;

        for (int i = 0; i < placeholderMatches.size(); i++) {
            PlaceholderMatch placeholderMatch = placeholderMatches.get(i);
            
            String literalTextBeforePlaceholder = pattern.substring(lastEnd, placeholderMatch.start);
            regexBuilder.append(Pattern.quote(literalTextBeforePlaceholder));

            String placeholderName = placeholderMatch.name;
            PlaceholderConfig config = PLACEHOLDER_CONFIGS.get(placeholderName);
            
            boolean isLastPlaceholder = (i == placeholderMatches.size() - 1);
            boolean hasTextAfterPlaceholder = (placeholderMatch.end < pattern.length());
            boolean shouldUseGreedyMatching = isLastPlaceholder && !hasTextAfterPlaceholder;

            String regexForPlaceholder = determineRegexForPlaceholder(config, shouldUseGreedyMatching);
            regexBuilder.append(regexForPlaceholder);
            log.debug("Placeholder {}: using regex '{}' (greedy={})", placeholderName, regexForPlaceholder, shouldUseGreedyMatching);
            
            placeholderOrder.add(placeholderName);
            lastEnd = placeholderMatch.end;
        }

        String literalTextAfterLastPlaceholder = pattern.substring(lastEnd);
        regexBuilder.append(Pattern.quote(literalTextAfterLastPlaceholder));
        
        String finalRegex = regexBuilder.toString();
        log.debug("Final compiled regex pattern: '{}'", finalRegex);

        try {
            Pattern compiledPattern = Pattern.compile(finalRegex);
            return new ParsedPattern(compiledPattern, placeholderOrder);
        } catch (Exception e) {
            log.error("Failed to compile pattern: {}", e.getMessage());
            return null;
        }
    }

    private List<PlaceholderMatch> findAllPlaceholders(String pattern) {
        List<PlaceholderMatch> placeholderMatches = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(pattern);
        
        while (matcher.find()) {
            placeholderMatches.add(new PlaceholderMatch(
                matcher.start(),
                matcher.end(),
                matcher.group(1)
            ));
        }
        
        return placeholderMatches;
    }

    private String determineRegexForPlaceholder(PlaceholderConfig config, boolean shouldUseGreedyMatching) {
        if (config != null) {
            String configuredRegex = config.regex();
            boolean isNonGreedyTextPattern = configuredRegex.equals("(.+?)");
            
            if (shouldUseGreedyMatching && isNonGreedyTextPattern) {
                return "(.+)";
            }
            return configuredRegex;
        }
        
        return shouldUseGreedyMatching ? "(.+)" : "(.+?)";
    }

    private BookMetadata buildMetadataFromMatch(Matcher matcher, List<String> placeholderOrder) {
        BookMetadata metadata = new BookMetadata();

        for (int i = 0; i < placeholderOrder.size(); i++) {
            String placeholderName = placeholderOrder.get(i);
            String value = matcher.group(i + 1).trim();
            applyValueToMetadata(metadata, placeholderName, value);
        }

        return metadata;
    }

    private void applyValueToMetadata(BookMetadata metadata, String placeholderName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        switch (placeholderName) {
            case "SeriesName" -> metadata.setSeriesName(value);
            case "Title" -> metadata.setTitle(value);
            case "Authors" -> metadata.setAuthors(parseAuthors(value));
            case "SeriesNumber", "SeriesBookNumber" -> parseAndSetSeriesNumber(metadata, value);
            case "Year" -> parseAndSetYear(metadata, value);
            case "Publisher" -> metadata.setPublisher(value);
            case "Language" -> metadata.setLanguage(value);
            case "SeriesTotal" -> parseAndSetSeriesTotal(metadata, value);
        }
    }

    private Set<String> parseAuthors(String value) {
        String[] parts = value.split("[,;&]");
        Set<String> authors = new LinkedHashSet<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                authors.add(trimmed);
            }
        }
        return authors;
    }

    private void parseAndSetSeriesNumber(BookMetadata metadata, String value) {
        try {
            metadata.setSeriesNumber(Float.parseFloat(value));
        } catch (NumberFormatException e) {
            log.debug("Could not parse series number: {}", value);
        }
    }

    private void parseAndSetYear(BookMetadata metadata, String value) {
        try {
            int year = Integer.parseInt(value);
            metadata.setPublishedDate(LocalDate.of(year, 1, 1));
        } catch (NumberFormatException e) {
            log.debug("Could not parse year: {}", value);
        }
    }

    private void parseAndSetSeriesTotal(BookMetadata metadata, String value) {
        try {
            metadata.setSeriesTotal(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            log.debug("Could not parse series total: {}", value);
        }
    }

    private record PlaceholderConfig(String regex, String metadataField) {}

    private record ParsedPattern(Pattern compiledPattern, List<String> placeholderOrder) {}
    
    private record PlaceholderMatch(int start, int end, String name) {}
}
