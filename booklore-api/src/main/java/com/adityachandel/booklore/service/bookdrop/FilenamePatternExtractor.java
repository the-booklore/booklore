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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilenamePatternExtractor {

    private final BookdropFileRepository bookdropFileRepository;

    private static final Map<String, PlaceholderConfig> PLACEHOLDER_CONFIGS = Map.ofEntries(
            Map.entry("SeriesName", new PlaceholderConfig("(.+?)", "seriesName")),
            Map.entry("Title", new PlaceholderConfig("(.+?)", "title")),
            Map.entry("Subtitle", new PlaceholderConfig("(.+?)", "subtitle")),
            Map.entry("Authors", new PlaceholderConfig("(.+?)", "authors")),
            Map.entry("SeriesNumber", new PlaceholderConfig("(\\d+(?:\\.\\d+)?)", "seriesNumber")),
            Map.entry("Published", new PlaceholderConfig("(.+?)", "publishedDate")),
            Map.entry("Publisher", new PlaceholderConfig("(.+?)", "publisher")),
            Map.entry("Language", new PlaceholderConfig("([a-zA-Z]+)", "language")),
            Map.entry("SeriesTotal", new PlaceholderConfig("(\\d+)", "seriesTotal")),
            Map.entry("ISBN10", new PlaceholderConfig("([0-9X]{10})", "isbn10")),
            Map.entry("ISBN13", new PlaceholderConfig("([0-9]{13})", "isbn13")),
            Map.entry("ASIN", new PlaceholderConfig("([A-Z0-9]{10})", "asin"))
    );

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)(?::(.*?))?}|\\*");

    public BookdropPatternExtractResult bulkExtract(BookdropPatternExtractRequest request) {
        List<Long> fileIds = resolveFileIds(request);
        List<BookdropFileEntity> files = bookdropFileRepository.findAllById(fileIds);
        
        boolean isPreview = Boolean.TRUE.equals(request.getPreview());
        int previewLimit = 5;
        List<BookdropFileEntity> filesToProcess = isPreview && files.size() > previewLimit
                ? files.subList(0, previewLimit)
                : files;

        List<BookdropPatternExtractResult.FileExtractionResult> results = new ArrayList<>();
        int successCount = 0;

        for (BookdropFileEntity file : filesToProcess) {
            BookdropPatternExtractResult.FileExtractionResult result = extractFromFile(file, request.getPattern());
            results.add(result);
            if (result.isSuccess()) {
                successCount++;
            }
        }

        return BookdropPatternExtractResult.builder()
                .totalFiles(files.size())
                .successfullyExtracted(successCount)
                .failed(filesToProcess.size() - successCount)
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
            String formatParameter = placeholderMatch.formatParameter;
            
            boolean isLastPlaceholder = (i == placeholderMatches.size() - 1);
            boolean hasTextAfterPlaceholder = (placeholderMatch.end < pattern.length());
            boolean shouldUseGreedyMatching = isLastPlaceholder && !hasTextAfterPlaceholder;

            String regexForPlaceholder;
            if ("*".equals(placeholderName)) {
                regexForPlaceholder = shouldUseGreedyMatching ? "(.+)" : "(.+?)";
                log.debug("Wildcard *: using regex '{}' (greedy={})", regexForPlaceholder, shouldUseGreedyMatching);
            } else if ("Published".equals(placeholderName) && formatParameter != null) {
                regexForPlaceholder = buildRegexForDateFormat(formatParameter);
                log.debug("Placeholder Published with format '{}': using regex '{}'", formatParameter, regexForPlaceholder);
            } else {
                PlaceholderConfig config = PLACEHOLDER_CONFIGS.get(placeholderName);
                regexForPlaceholder = determineRegexForPlaceholder(config, shouldUseGreedyMatching);
                log.debug("Placeholder {}: using regex '{}' (greedy={})", placeholderName, regexForPlaceholder, shouldUseGreedyMatching);
            }
            
            regexBuilder.append(regexForPlaceholder);
            
            String placeholderWithFormat = formatParameter != null ? placeholderName + ":" + formatParameter : placeholderName;
            placeholderOrder.add(placeholderWithFormat);
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
            String placeholderName;
            String formatParameter = null;
            
            if (matcher.group(0).equals("*")) {
                placeholderName = "*";
            } else {
                placeholderName = matcher.group(1);
                formatParameter = matcher.group(2);
            }
            
            placeholderMatches.add(new PlaceholderMatch(
                matcher.start(),
                matcher.end(),
                placeholderName,
                formatParameter
            ));
        }
        
        return placeholderMatches;
    }

    private String buildRegexForDateFormat(String dateFormat) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        
        while (i < dateFormat.length()) {
            if (dateFormat.startsWith("yyyy", i)) {
                result.append("\\d{4}");
                i += 4;
            } else if (dateFormat.startsWith("yy", i)) {
                result.append("\\d{2}");
                i += 2;
            } else if (dateFormat.startsWith("MM", i)) {
                result.append("\\d{2}");
                i += 2;
            } else if (dateFormat.startsWith("M", i)) {
                result.append("\\d{1,2}");
                i += 1;
            } else if (dateFormat.startsWith("dd", i)) {
                result.append("\\d{2}");
                i += 2;
            } else if (dateFormat.startsWith("d", i)) {
                result.append("\\d{1,2}");
                i += 1;
            } else {
                result.append(Pattern.quote(String.valueOf(dateFormat.charAt(i))));
                i++;
            }
        }
        
        return "(" + result.toString() + ")";
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
            String placeholderWithFormat = placeholderOrder.get(i);
            String[] parts = placeholderWithFormat.split(":", 2);
            String placeholderName = parts[0];
            String formatParameter = parts.length > 1 ? parts[1] : null;
            
            if ("*".equals(placeholderName)) {
                continue;
            }
            
            String value = matcher.group(i + 1).trim();
            applyValueToMetadata(metadata, placeholderName, value, formatParameter);
        }

        return metadata;
    }

    private void applyValueToMetadata(BookMetadata metadata, String placeholderName, String value, String formatParameter) {
        if (value == null || value.isBlank()) {
            return;
        }

        switch (placeholderName) {
            case "SeriesName" -> metadata.setSeriesName(value);
            case "Title" -> metadata.setTitle(value);
            case "Subtitle" -> metadata.setSubtitle(value);
            case "Authors" -> metadata.setAuthors(parseAuthors(value));
            case "SeriesNumber" -> parseAndSetSeriesNumber(metadata, value);
            case "Published" -> parseAndSetPublishedDate(metadata, value, formatParameter);
            case "Publisher" -> metadata.setPublisher(value);
            case "Language" -> metadata.setLanguage(value);
            case "SeriesTotal" -> parseAndSetSeriesTotal(metadata, value);
            case "ISBN10" -> metadata.setIsbn10(value);
            case "ISBN13" -> metadata.setIsbn13(value);
            case "ASIN" -> metadata.setAsin(value);
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

    private void parseAndSetPublishedDate(BookMetadata metadata, String value, String dateFormat) {
        String detectedFormat = (dateFormat == null || dateFormat.isBlank()) 
                ? detectDateFormat(value) 
                : dateFormat;
        
        if (detectedFormat == null) {
            log.debug("Could not detect date format for value: '{}'", value);
            return;
        }
        
        try {
            if ("yyyy".equals(detectedFormat) || "yy".equals(detectedFormat)) {
                int year = Integer.parseInt(value);
                if ("yy".equals(detectedFormat) && year < 100) {
                    year += (year < 50) ? 2000 : 1900;
                }
                metadata.setPublishedDate(LocalDate.of(year, 1, 1));
                return;
            }
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(detectedFormat);
            LocalDate date = LocalDate.parse(value, formatter);
            metadata.setPublishedDate(date);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            log.debug("Could not parse published date '{}' with format '{}': {}", value, detectedFormat, e.getMessage());
        }
    }

    private String detectDateFormat(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        
        String trimmedValue = value.trim();
        int length = trimmedValue.length();
        
        if (length == 4 && trimmedValue.matches("\\d{4}")) {
            return "yyyy";
        }
        
        if (length == 2 && trimmedValue.matches("\\d{2}")) {
            return "yy";
        }
        
        if (trimmedValue.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            boolean hasPaddedMonthAndDay = trimmedValue.matches("\\d{4}-\\d{2}-\\d{2}");
            return hasPaddedMonthAndDay ? "yyyy-MM-dd" : "yyyy-M-d";
        }
        
        if (trimmedValue.matches("\\d{4}\\.\\d{1,2}\\.\\d{1,2}")) {
            boolean hasPaddedMonthAndDay = trimmedValue.matches("\\d{4}\\.\\d{2}\\.\\d{2}");
            return hasPaddedMonthAndDay ? "yyyy.MM.dd" : "yyyy.M.d";
        }
        
        if (trimmedValue.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
            boolean hasPaddedMonthAndDay = trimmedValue.matches("\\d{4}/\\d{2}/\\d{2}");
            return hasPaddedMonthAndDay ? "yyyy/MM/dd" : "yyyy/M/d";
        }
        
        if (length == 8 && trimmedValue.matches("\\d{8}")) {
            return "yyyyMMdd";
        }
        
        log.debug("No date format detected for value: '{}'", value);
        return null;
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
    
    private record PlaceholderMatch(int start, int end, String name, String formatParameter) {}
}
