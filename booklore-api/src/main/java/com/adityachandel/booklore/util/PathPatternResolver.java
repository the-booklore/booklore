package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
@Slf4j
public class PathPatternResolver {

    private final int MAX_COMPONENT_BYTES = 200;
    private final int MAX_FILESYSTEM_COMPONENT_BYTES = 245; // Left 10 bytes buffer
    private final int MAX_AUTHOR_BYTES = 180;

    private final String TRUNCATION_SUFFIX = " et al.";
    private final int SUFFIX_BYTES = TRUNCATION_SUFFIX.getBytes(StandardCharsets.UTF_8).length;

    private final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private final Pattern CONTROL_CHARACTER_PATTERN = Pattern.compile("\\p{Cntrl}");
    private final Pattern INVALID_CHARS_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");
    private final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(.*?)}");
    private final Pattern COMMA_SPACE_PATTERN = Pattern.compile(", ");
    private final Pattern SLASH_PATTERN = Pattern.compile("/");
    private final Pattern COMBINING_DIACRITICAL_MARKS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    public String resolvePattern(BookEntity book, String pattern) {
        String currentFilename = book.getFileName() != null ? book.getFileName().trim() : "";
        MetadataProvider metadataProvider = MetadataProvider.from(book.getMetadata());
        return resolvePattern(metadataProvider, pattern, currentFilename);
    }

    public String resolvePattern(BookMetadata metadata, String pattern, String filename) {
        MetadataProvider metadataProvider = MetadataProvider.from(metadata);
        return resolvePattern(metadataProvider, pattern, filename);
    }

    public String resolvePattern(BookMetadataEntity metadata, String pattern, String filename) {
        MetadataProvider metadataProvider = MetadataProvider.from(metadata);
        return resolvePattern(metadataProvider, pattern, filename);
    }

    private String resolvePattern(MetadataProvider metadata, String pattern, String currentFilename) {
        if (pattern == null || pattern.isBlank()) {
            return currentFilename;
        }

        String filenameBase = "Untitled";
        if (currentFilename != null && !currentFilename.isBlank()) {
            int lastDot = currentFilename.lastIndexOf('.');
            if (lastDot > 0) {
                filenameBase = currentFilename.substring(0, lastDot);
            } else {
                filenameBase = currentFilename;
            }
        }

        String title = sanitize(metadata != null && metadata.getTitle() != null
                ? metadata.getTitle()
                : filenameBase);

        String subtitle = sanitize(metadata != null ? metadata.getSubtitle() : "");

        String authors = sanitize(
                metadata != null
                        ? truncateAuthorsForFilesystem(String.join(", ", metadata.getAuthors()))
                        : ""
        );

        String author = sanitize(
            metadata != null && metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()
                ? metadata.getAuthors().get(0)
                : ""
        );
        String authorId = sanitize(
            metadata != null
                ? metadata.getFirstAuthorId().map(String::valueOf).orElse("")
                : ""
        );
        String year = sanitize(
                metadata != null && metadata.getPublishedDate() != null
                        ? String.valueOf(metadata.getPublishedDate().getYear())
                        : ""
        );
        String series = sanitize(metadata != null ? metadata.getSeriesName() : "");
        String seriesIndex = "";
        if (metadata != null && metadata.getSeriesNumber() != null) {
            Float seriesNumber = metadata.getSeriesNumber();
            seriesIndex = (seriesNumber % 1 == 0)
                    ? String.valueOf(seriesNumber.intValue())
                    : seriesNumber.toString();
            seriesIndex = sanitize(seriesIndex);
        }
        String language = sanitize(metadata != null ? metadata.getLanguage() : "");
        String publisher = sanitize(metadata != null ? metadata.getPublisher() : "");
        String isbn = sanitize(
                metadata != null
                        ? (metadata.getIsbn13() != null
                        ? metadata.getIsbn13()
                        : metadata.getIsbn10() != null
                        ? metadata.getIsbn10()
                        : "")
                        : ""
        );

        Map<String, String> values = new LinkedHashMap<>();
        values.put("author", truncatePathComponent(author, MAX_COMPONENT_BYTES));
        values.put("author_id", authorId);
        values.put("authors", authors);
        values.put("currentFilename", sanitize(currentFilename));
        values.put("title", truncatePathComponent(title, MAX_COMPONENT_BYTES));
        values.put("subtitle", truncatePathComponent(subtitle, MAX_COMPONENT_BYTES));
        values.put("title_sortable", truncatePathComponent(normalizeForSorting(title), MAX_COMPONENT_BYTES));
        values.put("year", year);
        values.put("series", truncatePathComponent(series, MAX_COMPONENT_BYTES));
        values.put("series_sortable", truncatePathComponent(normalizeForSorting(series), MAX_COMPONENT_BYTES));
        values.put("seriesIndex", seriesIndex);
        values.put("language", language);
        values.put("publisher", truncatePathComponent(publisher, MAX_COMPONENT_BYTES));
        values.put("publisher_sortable", truncatePathComponent(normalizeForSorting(publisher), MAX_COMPONENT_BYTES));
        values.put("isbn", isbn);

        values.put("author_sortable", truncatePathComponent(normalizeForSorting(author), MAX_COMPONENT_BYTES));

        return resolvePatternWithValues(pattern, values, currentFilename);
    }

    private String normalizeForSorting(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // TR03-1999 (NISO TR03) key points applied here:
        // - 3.1: collapse contiguous spaces
        // - 3.2: hyphen/dash/slash treated as spaces
        // - 3.3: listed punctuation marks ignored (not treated as spaces)
        // - 3.4: contiguous symbols treated as a single character
        // - 3.6.1: modified letters arranged as nearest basic equivalents
        // - 4.6: initial articles are NOT removed automatically
        // - 6.3: decimal point is significant for decimal fractions

        String s = input;

        // 3.2: treat hyphen/dash (any length) and slash as space
        s = s.replaceAll("[-\\u2010\\u2011\\u2012\\u2013\\u2014\\u2212/]", " ");

        StringBuilder out = new StringBuilder(s.length());
        boolean lastWasSpace = false;
        boolean lastWasSymbol = false;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            if (Character.isWhitespace(ch)) {
                if (!lastWasSpace && !out.isEmpty()) {
                    out.append(' ');
                }
                lastWasSpace = true;
                lastWasSymbol = false;
                continue;
            }

            // 3.3: punctuation marks ignored (not treated as spaces)
            // with 6.3 exception: keep '.' only when it is a decimal point
            if (isIgnoredPunctuation(ch)) {
                if (ch == '.') {
                    boolean nextIsDigit = (i + 1) < s.length() && Character.isDigit(s.charAt(i + 1));
                    boolean prevIsDigitOrBoundary = (i == 0)
                            || Character.isDigit(s.charAt(i - 1))
                            || Character.isWhitespace(s.charAt(i - 1));

                    if (nextIsDigit && prevIsDigitOrBoundary) {
                        // 6.3: preserve decimal point
                        out.append('.');
                        lastWasSpace = false;
                        lastWasSymbol = false;
                    }
                }
                continue;
            }

            // 3.4: contiguous symbols treated as single character
            boolean isLetterOrDigit = Character.isLetterOrDigit(ch);
            boolean isSymbol = !isLetterOrDigit;
            if (isSymbol) {
                if (lastWasSymbol) {
                    continue;
                }
                lastWasSymbol = true;
            } else {
                lastWasSymbol = false;
            }

            out.append(ch);
            lastWasSpace = false;
        }

        // 3.6.1: normalize modified letters to nearest basic equivalents
        String normalized = java.text.Normalizer.normalize(out.toString(), java.text.Normalizer.Form.NFD);
        normalized = COMBINING_DIACRITICAL_MARKS_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized
                .replace("ø", "o").replace("Ø", "O")
                .replace("ł", "l").replace("Ł", "L")
                .replace("æ", "ae").replace("Æ", "AE")
                .replace("œ", "oe").replace("Œ", "OE")
                .replace("ß", "ss");

        return sanitize(normalized);
    }

    private boolean isIgnoredPunctuation(char ch) {
        return switch (ch) {
            case '.', ',', ';', ':', '(', ')', '[', ']', '<', '>', '{', '}',
                 '\'', '"', '!', '?' -> true;
            // Common Unicode apostrophes/quotes are treated as ignored punctuation too
            case '\u2018', '\u2019', '\u201A', '\u201B', // single quotes
                 '\u201C', '\u201D', '\u201E', '\u201F'  // double quotes
                    -> true;
            default -> false;
        };
    }

    private String resolvePatternWithValues(String pattern, Map<String, String> values, String currentFilename) {
        String extension = "";
        int lastDot = currentFilename.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < currentFilename.length() - 1) {
            extension = sanitize(currentFilename.substring(lastDot + 1));  // e.g. "epub"
        }

        values.put("extension", extension);

        // Handle optional blocks enclosed in <...>
        Pattern optionalBlockPattern = Pattern.compile("<([^<>]*)>");
        Matcher matcher = optionalBlockPattern.matcher(pattern);
        StringBuilder resolved = new StringBuilder(1024);

        while (matcher.find()) {
            String block = matcher.group(1);
            Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(block);
            boolean allHaveValues = true;

            // Check if all placeholders inside optional block have non-blank values
            while (placeholderMatcher.find()) {
                String key = placeholderMatcher.group(1);
                String value = values.getOrDefault(key, "");
                if (value.isBlank()) {
                    allHaveValues = false;
                    break;
                }
            }

            if (allHaveValues) {
                String resolvedBlock = block;
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    resolvedBlock = resolvedBlock.replace("{" + entry.getKey() + "}", entry.getValue());
                }
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(resolvedBlock));
            } else {
                matcher.appendReplacement(resolved, "");
            }
        }
        matcher.appendTail(resolved);

        String result = resolved.toString();

        // Replace known placeholders with values, preserve unknown ones
        Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(result);
        StringBuilder finalResult = new StringBuilder(1024);

        while (placeholderMatcher.find()) {
            String key = placeholderMatcher.group(1);
            if (values.containsKey(key)) {
                String replacement = values.get(key);
                placeholderMatcher.appendReplacement(finalResult, Matcher.quoteReplacement(replacement));
            } else {
                // {originalFilename} is intentionally unsupported; replace with empty
                // to avoid emitting literal braces into filesystem paths.
                if ("originalFilename".equals(key)) {
                    placeholderMatcher.appendReplacement(finalResult, "");
                } else {
                    // Preserve unknown placeholders (e.g., {foo})
                    placeholderMatcher.appendReplacement(finalResult, Matcher.quoteReplacement("{" + key + "}"));
                }
            }
        }
        placeholderMatcher.appendTail(finalResult);

        result = finalResult.toString();

        boolean usedFallbackFilename = false;
        if (result.isBlank()) {
            result = currentFilename != null && !currentFilename.isBlank() ? currentFilename : "untitled";
            usedFallbackFilename = true;
        }

        boolean patternIncludesExtension = pattern.contains("{extension}") || pattern.contains("{currentFilename}");

        if (!usedFallbackFilename && !patternIncludesExtension && !extension.isBlank()) {
            result += "." + extension;
        }

        return validateFinalPath(result);
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return WHITESPACE_PATTERN.matcher(CONTROL_CHARACTER_PATTERN.matcher(INVALID_CHARS_PATTERN.matcher(input).replaceAll("")).replaceAll("")).replaceAll(" ")
                .trim();
    }

    private String truncateAuthorsForFilesystem(String authors) {
        if (authors == null || authors.isEmpty()) {
            return authors;
        }

        byte[] originalBytes = authors.getBytes(StandardCharsets.UTF_8);
        if (originalBytes.length <= MAX_AUTHOR_BYTES) {
            return authors;
        }

        String[] authorArray = COMMA_SPACE_PATTERN.split(authors);
        StringBuilder result = new StringBuilder(256);
        int currentBytes = 0;
        int truncationLimit = MAX_AUTHOR_BYTES - SUFFIX_BYTES;

        for (int i = 0; i < authorArray.length; i++) {
            String author = authorArray[i];

            int separatorBytes = (i > 0) ? 2 : 0;
            int authorBytes = author.getBytes(StandardCharsets.UTF_8).length;

            if (currentBytes + separatorBytes + authorBytes > MAX_AUTHOR_BYTES) {
                if (result.isEmpty()) {
                     return truncatePathComponent(author, truncationLimit) + TRUNCATION_SUFFIX;
                }
                return result + TRUNCATION_SUFFIX;
            }

            if (i > 0) {
                result.append(", ");
                currentBytes += 2;
            }
            result.append(author);
            currentBytes += authorBytes;
        }

        return result.toString();
    }

    public String truncatePathComponent(String component, int maxBytes) {
        if (component == null || component.isEmpty()) {
            return component;
        }

        byte[] bytes = component.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return component;
        }

        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        ByteBuffer buffer = ByteBuffer.allocate(maxBytes);
        CharBuffer charBuffer = CharBuffer.wrap(component);

        encoder.encode(charBuffer, buffer, true);

        String truncated = component.substring(0, charBuffer.position());
        if (!truncated.equals(component)) {
            log.debug("Truncated path component from {} to {} bytes for filesystem safety",
                bytes.length, truncated.getBytes(StandardCharsets.UTF_8).length);
        }
        return truncated;
    }


    private String validateFinalPath(String path) {
        String[] components = SLASH_PATTERN.split(path);
        StringBuilder result = new StringBuilder(512);

        for (int i = 0; i < components.length; i++) {
            String component = components[i];
            boolean isLastComponent = (i == components.length - 1);

            if (isLastComponent && component.contains(".")) {
                component = truncateFilenameWithExtension(component);
            } else {
                if (component.getBytes(StandardCharsets.UTF_8).length > MAX_FILESYSTEM_COMPONENT_BYTES) {
                    component = truncatePathComponent(component, MAX_FILESYSTEM_COMPONENT_BYTES);
                }
                while (component != null && !component.isEmpty() && component.endsWith(".")) {
                    component = component.substring(0, component.length() - 1);
                }
            }

            if (i > 0) result.append("/");
            result.append(component);
        }
        return result.toString();
    }

    public String truncateFilenameWithExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == 0) {
            // No extension or dot is at start (hidden file), treat as normal component
            if (filename.getBytes(StandardCharsets.UTF_8).length > MAX_FILESYSTEM_COMPONENT_BYTES) {
                return truncatePathComponent(filename, MAX_FILESYSTEM_COMPONENT_BYTES);
            }
            return filename;
        }

        String extension = filename.substring(lastDotIndex); // includes dot
        String name = filename.substring(0, lastDotIndex);

        int extBytes = extension.getBytes(StandardCharsets.UTF_8).length;

        if (extBytes > 50) {
            log.warn("Unusually long extension detected: {}", extension);
            if (filename.getBytes(StandardCharsets.UTF_8).length > MAX_FILESYSTEM_COMPONENT_BYTES) {
                 return truncatePathComponent(filename, MAX_FILESYSTEM_COMPONENT_BYTES);
            }
            return filename;
        }

        int maxNameBytes = MAX_FILESYSTEM_COMPONENT_BYTES - extBytes;

        if (name.getBytes(StandardCharsets.UTF_8).length > maxNameBytes) {
            String truncatedName = truncatePathComponent(name, maxNameBytes);
            return truncatedName + extension;
        }

        return filename;
    }

    private interface MetadataProvider {
        String getTitle();

        String getSubtitle();

        List<String> getAuthors();

        default Optional<Long> getFirstAuthorId() {
            return Optional.empty();
        }

        Integer getYear();

        String getSeriesName();

        Float getSeriesNumber();

        String getLanguage();

        String getPublisher();

        String getIsbn13();

        String getIsbn10();

        LocalDate getPublishedDate();

        static BookMetadataProvider from(BookMetadata metadata) {
            if (metadata == null) {
                return null;
            }

            return new BookMetadataProvider(metadata);
        }

        static BookMetadataEntityProvider from(BookMetadataEntity metadata) {
            if (metadata == null) {
                return null;
            }

            return new BookMetadataEntityProvider(metadata);
        }
    }

    private record BookMetadataProvider(BookMetadata metadata) implements MetadataProvider {

        @Override
        public String getTitle() {
            return metadata.getTitle();
        }

        @Override
        public String getSubtitle() {
            return metadata.getSubtitle();
        }

        @Override
        public List<String> getAuthors() {
            return metadata.getAuthors() != null ? metadata.getAuthors().stream().toList() : Collections.emptyList();
        }

        @Override
        public Integer getYear() {
            return metadata.getPublishedDate() != null ? metadata.getPublishedDate().getYear() : null;
        }

        @Override
        public String getSeriesName() {
            return metadata.getSeriesName();
        }

        @Override
        public Float getSeriesNumber() {
            return metadata.getSeriesNumber();
        }

        @Override
        public String getLanguage() {
            return metadata.getLanguage();
        }

        @Override
        public String getPublisher() {
            return metadata.getPublisher();
        }

        @Override
        public String getIsbn13() {
            return metadata.getIsbn13();
        }

        @Override
        public String getIsbn10() {
            return metadata.getIsbn10();
        }

        @Override
        public LocalDate getPublishedDate() {
            return metadata.getPublishedDate();
        }
    }

    private record BookMetadataEntityProvider(BookMetadataEntity metadata) implements MetadataProvider {

        @Override
        public String getTitle() {
            return metadata.getTitle();
        }

        @Override
        public String getSubtitle() {
            return metadata.getSubtitle();
        }

        @Override
        public List<String> getAuthors() {
            return metadata.getAuthors() != null
                    ? metadata.getAuthors()
                    .stream()
                    .map(AuthorEntity::getName)
                    .toList()
                    : Collections.emptyList();
        }

        @Override
        public Optional<Long> getFirstAuthorId() {
            if (metadata.getAuthors() == null) {
                return Optional.empty();
            }
            return metadata.getAuthors().stream().findFirst().map(AuthorEntity::getId);
        }

        @Override
        public Integer getYear() {
            return metadata.getPublishedDate() != null ? metadata.getPublishedDate().getYear() : null;
        }

        @Override
        public String getSeriesName() {
            return metadata.getSeriesName();
        }

        @Override
        public Float getSeriesNumber() {
            return metadata.getSeriesNumber();
        }

        @Override
        public String getLanguage() {
            return metadata.getLanguage();
        }

        @Override
        public String getPublisher() {
            return metadata.getPublisher();
        }

        @Override
        public String getIsbn13() {
            return metadata.getIsbn13();
        }

        @Override
        public String getIsbn10() {
            return metadata.getIsbn10();
        }

        @Override
        public LocalDate getPublishedDate() {
            return metadata.getPublishedDate();
        }
    }
}
