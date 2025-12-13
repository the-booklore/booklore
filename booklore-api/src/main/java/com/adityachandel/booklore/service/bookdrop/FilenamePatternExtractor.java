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
import org.springframework.transaction.annotation.Transactional;

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
    private final BookdropMetadataHelper metadataHelper;
    
    private static final int PREVIEW_FILE_LIMIT = 5;
    private static final int TWO_DIGIT_YEAR_CUTOFF = 50;
    private static final int TWO_DIGIT_YEAR_CENTURY_BASE = 1900;
    private static final int FOUR_DIGIT_YEAR_LENGTH = 4;
    private static final int TWO_DIGIT_YEAR_LENGTH = 2;
    private static final int COMPACT_DATE_LENGTH = 8;

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
    
    private static final Pattern FOUR_DIGIT_YEAR_PATTERN = Pattern.compile("\\d{4}");
    private static final Pattern TWO_DIGIT_YEAR_PATTERN = Pattern.compile("\\d{2}");
    private static final Pattern COMPACT_DATE_PATTERN = Pattern.compile("\\d{8}");
    private static final Pattern FLEXIBLE_DATE_PATTERN = Pattern.compile("(\\d{1,4})([^\\d])(\\d{1,2})\\2(\\d{1,4})");

    @Transactional
    public BookdropPatternExtractResult bulkExtract(BookdropPatternExtractRequest request) {
        List<Long> fileIds = metadataHelper.resolveFileIds(
                Boolean.TRUE.equals(request.getSelectAll()),
                request.getExcludedIds(),
                request.getSelectedIds()
        );
        List<BookdropFileEntity> files = bookdropFileRepository.findAllById(fileIds);
        
        boolean isPreview = Boolean.TRUE.equals(request.getPreview());
        List<BookdropFileEntity> filesToProcess = isPreview && files.size() > PREVIEW_FILE_LIMIT
                ? files.subList(0, PREVIEW_FILE_LIMIT)
                : files;

        ParsedPattern cachedPattern = parsePattern(request.getPattern());
        if (cachedPattern == null) {
            log.error("Failed to parse pattern: '{}'", request.getPattern());
            return buildEmptyResult(files.size());
        }

        List<BookdropPatternExtractResult.FileExtractionResult> results = new ArrayList<>();
        int successCount = 0;

        for (BookdropFileEntity file : filesToProcess) {
            BookdropPatternExtractResult.FileExtractionResult result = extractFromFile(file, cachedPattern);
            results.add(result);
            if (result.isSuccess()) {
                successCount++;
            }
        }

        if (!isPreview) {
            persistExtractedMetadata(results, files);
        }

        return BookdropPatternExtractResult.builder()
                .totalFiles(files.size())
                .successfullyExtracted(successCount)
                .failed(filesToProcess.size() - successCount)
                .results(results)
                .build();
    }
    
    private BookdropPatternExtractResult buildEmptyResult(int totalFiles) {
        return BookdropPatternExtractResult.builder()
                .totalFiles(totalFiles)
                .successfullyExtracted(0)
                .failed(totalFiles)
                .results(Collections.emptyList())
                .build();
    }

    public BookMetadata extractFromFilename(String filename, String pattern) {
        ParsedPattern parsedPattern = parsePattern(pattern);
        if (parsedPattern == null) {
            return null;
        }
        
        return extractFromFilenameWithParsedPattern(filename, parsedPattern);
    }
    
    private BookMetadata extractFromFilenameWithParsedPattern(String filename, ParsedPattern parsedPattern) {
        String nameOnly = FilenameUtils.getBaseName(filename);
        Matcher extractMatcher = parsedPattern.compiledPattern().matcher(nameOnly);
        
        boolean matched = extractMatcher.find();
        if (!matched) {
            return null;
        }
        
        return buildMetadataFromMatch(extractMatcher, parsedPattern.placeholderOrder());
    }

    private BookdropPatternExtractResult.FileExtractionResult extractFromFile(
            BookdropFileEntity file, 
            ParsedPattern parsedPattern) {
        try {
            BookMetadata extracted = extractFromFilenameWithParsedPattern(file.getFileName(), parsedPattern);
            
            if (extracted == null) {
                String errorMsg = "Pattern did not match filename structure. Check if the pattern aligns with the filename format.";
                log.debug("Pattern mismatch for file '{}'", file.getFileName());
                return BookdropPatternExtractResult.FileExtractionResult.builder()
                        .fileId(file.getId())
                        .fileName(file.getFileName())
                        .success(false)
                        .errorMessage(errorMsg)
                        .build();
            }

            return BookdropPatternExtractResult.FileExtractionResult.builder()
                    .fileId(file.getId())
                    .fileName(file.getFileName())
                    .success(true)
                    .extractedMetadata(extracted)
                    .build();

        } catch (Exception e) {
            String errorMsg = "Extraction failed: " + e.getMessage();
            log.error("Error extracting metadata from file '{}': {}", file.getFileName(), e.getMessage(), e);
            return BookdropPatternExtractResult.FileExtractionResult.builder()
                    .fileId(file.getId())
                    .fileName(file.getFileName())
                    .success(false)
                    .errorMessage(errorMsg)
                    .build();
        }
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
            } else if ("Published".equals(placeholderName) && formatParameter != null) {
                regexForPlaceholder = buildRegexForDateFormat(formatParameter);
            } else {
                PlaceholderConfig config = PLACEHOLDER_CONFIGS.get(placeholderName);
                regexForPlaceholder = determineRegexForPlaceholder(config, shouldUseGreedyMatching);
            }
            
            regexBuilder.append(regexForPlaceholder);
            
            String placeholderWithFormat = formatParameter != null ? placeholderName + ":" + formatParameter : placeholderName;
            placeholderOrder.add(placeholderWithFormat);
            lastEnd = placeholderMatch.end;
        }

        String literalTextAfterLastPlaceholder = pattern.substring(lastEnd);
        regexBuilder.append(Pattern.quote(literalTextAfterLastPlaceholder));
        
        try {
            Pattern compiledPattern = Pattern.compile(regexBuilder.toString());
            return new ParsedPattern(compiledPattern, placeholderOrder);
        } catch (Exception e) {
            log.error("Failed to compile regex pattern from user input '{}': {}", pattern, e.getMessage());
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
            case "SeriesNumber" -> setSeriesNumber(metadata, value);
            case "Published" -> setPublishedDate(metadata, value, formatParameter);
            case "Publisher" -> metadata.setPublisher(value);
            case "Language" -> metadata.setLanguage(value);
            case "SeriesTotal" -> setSeriesTotal(metadata, value);
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

    private void setSeriesNumber(BookMetadata metadata, String value) {
        try {
            metadata.setSeriesNumber(Float.parseFloat(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private void setPublishedDate(BookMetadata metadata, String value, String dateFormat) {
        String detectedFormat = (dateFormat == null || dateFormat.isBlank()) 
                ? detectDateFormat(value) 
                : dateFormat;
        
        if (detectedFormat == null) {
            log.warn("Could not detect date format for value: '{}'", value);
            return;
        }
        
        try {
            if ("yyyy".equals(detectedFormat) || "yy".equals(detectedFormat)) {
                int year = Integer.parseInt(value);
                if ("yy".equals(detectedFormat) && year < 100) {
                    year += (year < TWO_DIGIT_YEAR_CUTOFF) ? 2000 : TWO_DIGIT_YEAR_CENTURY_BASE;
                }
                metadata.setPublishedDate(LocalDate.of(year, 1, 1));
                return;
            }
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(detectedFormat);
            LocalDate date = LocalDate.parse(value, formatter);
            metadata.setPublishedDate(date);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date '{}' with format '{}': {}", value, detectedFormat, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid date format '{}' for value '{}': {}", detectedFormat, value, e.getMessage());
        }
    }

    private String detectDateFormat(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        
        String trimmed = value.trim();
        int length = trimmed.length();
        
        if (length == FOUR_DIGIT_YEAR_LENGTH && FOUR_DIGIT_YEAR_PATTERN.matcher(trimmed).matches()) {
            return "yyyy";
        }
        
        if (length == TWO_DIGIT_YEAR_LENGTH && TWO_DIGIT_YEAR_PATTERN.matcher(trimmed).matches()) {
            return "yy";
        }
        
        if (length == COMPACT_DATE_LENGTH && COMPACT_DATE_PATTERN.matcher(trimmed).matches()) {
            return "yyyyMMdd";
        }
        
        Matcher flexibleMatcher = FLEXIBLE_DATE_PATTERN.matcher(trimmed);
        if (flexibleMatcher.matches()) {
            String separator = flexibleMatcher.group(2);
            return determineFlexibleDateFormat(flexibleMatcher, separator);
        }
        
        return null;
    }
    
    private String determineFlexibleDateFormat(Matcher matcher, String separator) {
        String part1 = matcher.group(1);
        String part2 = matcher.group(3);
        String part3 = matcher.group(4);
        
        int val1 = Integer.parseInt(part1);
        int val2 = Integer.parseInt(part2);
        int val3 = Integer.parseInt(part3);
        
        String format1, format2, format3;
        
        if (isYearValue(part1, val1)) {
            format1 = buildYearFormat(part1);
            if (val2 <= 12 && val3 > 12) {
                format2 = buildMonthFormat(part2);
                format3 = buildDayFormat(part3);
            } else if (val3 <= 12 && val2 > 12) {
                format2 = buildDayFormat(part2);
                format3 = buildMonthFormat(part3);
            } else {
                format2 = buildMonthFormat(part2);
                format3 = buildDayFormat(part3);
            }
        } else if (isYearValue(part3, val3)) {
            format3 = buildYearFormat(part3);
            if (val1 <= 12 && val2 > 12) {
                format1 = buildMonthFormat(part1);
                format2 = buildDayFormat(part2);
            } else if (val2 <= 12 && val1 > 12) {
                format1 = buildDayFormat(part1);
                format2 = buildMonthFormat(part2);
            } else {
                format1 = buildDayFormat(part1);
                format2 = buildMonthFormat(part2);
            }
        } else {
            format1 = buildDayFormat(part1);
            format2 = buildMonthFormat(part2);
            format3 = part3.length() == 2 ? "yy" : "y";
        }
        
        return format1 + separator + format2 + separator + format3;
    }
    
    private boolean isYearValue(String part, int value) {
        return part.length() == 4 || value > 31;
    }
    
    private String buildYearFormat(String part) {
        return part.length() == 4 ? "yyyy" : "yy";
    }
    
    private String buildMonthFormat(String part) {
        return part.length() == 2 ? "MM" : "M";
    }
    
    private String buildDayFormat(String part) {
        return part.length() == 2 ? "dd" : "d";
    }

    private void setSeriesTotal(BookMetadata metadata, String value) {
        try {
            metadata.setSeriesTotal(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private void persistExtractedMetadata(List<BookdropPatternExtractResult.FileExtractionResult> results, List<BookdropFileEntity> files) {
        Map<Long, BookdropFileEntity> fileMap = new HashMap<>();
        for (BookdropFileEntity file : files) {
            fileMap.put(file.getId(), file);
        }

        for (BookdropPatternExtractResult.FileExtractionResult result : results) {
            if (!result.isSuccess() || result.getExtractedMetadata() == null) {
                continue;
            }

            BookdropFileEntity file = fileMap.get(result.getFileId());
            if (file == null) {
                continue;
            }

            try {
                BookMetadata currentMetadata = metadataHelper.getCurrentMetadata(file);
                BookMetadata extractedMetadata = result.getExtractedMetadata();
                metadataHelper.mergeMetadata(currentMetadata, extractedMetadata);
                metadataHelper.updateFetchedMetadata(file, currentMetadata);

            } catch (Exception e) {
                log.error("Error persisting extracted metadata for file {}: {}", file.getId(), e.getMessage());
            }
        }

        bookdropFileRepository.saveAll(files);
    }

    private record PlaceholderConfig(String regex, String metadataField) {}

    private record ParsedPattern(Pattern compiledPattern, List<String> placeholderOrder) {}
    
    private record PlaceholderMatch(int start, int end, String name, String formatParameter) {}
}
