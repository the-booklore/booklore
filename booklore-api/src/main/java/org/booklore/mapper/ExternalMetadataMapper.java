package org.booklore.mapper;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.external.ExternalBookMetadata;
import org.booklore.model.dto.external.ExternalCoverImage;
import org.booklore.model.dto.external.ExternalSeriesInfo;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;

/**
 * Maps DTOs from the external metadata provider API spec to BookLore's internal DTOs.
 * This is a manual mapper (not MapStruct) because it requires custom logic for
 * series selection, date parsing, and field mapping that don't map 1:1.
 */
@Component
public class ExternalMetadataMapper {

    /**
     * Maps an external BookMetadata response to the internal BookMetadata DTO.
     *
     * @param external    the metadata from the external provider
     * @param bookTitle   the current book title, used as context for series selection heuristics (nullable)
     * @return the mapped internal BookMetadata
     */
    public BookMetadata toBookMetadata(ExternalBookMetadata external, String bookTitle) {
        if (external == null) {
            return null;
        }

        BookMetadata.BookMetadataBuilder builder = BookMetadata.builder()
                .title(external.getTitle())
                .subtitle(external.getSubtitle())
                .publisher(external.getPublisher())
                .publishedDate(parseDate(external.getPublishedDate()))
                .description(external.getDescription())
                .pageCount(external.getPageCount())
                .language(external.getLanguage())
                .isbn13(external.getIsbn13())
                .isbn10(external.getIsbn10())
                .asin(external.getAsin())
                .thumbnailUrl(external.getThumbnailUrl())
                .rating(external.getRating())
                .externalUrl(null);

        if (external.getAuthors() != null) {
            builder.authors(new HashSet<>(external.getAuthors()));
        }
        if (external.getCategories() != null) {
            builder.categories(new HashSet<>(external.getCategories()));
        }
        if (external.getMoods() != null) {
            builder.moods(new HashSet<>(external.getMoods()));
        }
        if (external.getTags() != null) {
            builder.tags(new HashSet<>(external.getTags()));
        }

        ExternalSeriesInfo bestSeries = pickBestSeries(external.getSeries(), bookTitle);
        if (bestSeries != null) {
            builder.seriesName(bestSeries.getName())
                    .seriesNumber(bestSeries.getNumber())
                    .seriesTotal(bestSeries.getTotal());
        }

        return builder.build();
    }

    /**
     * Maps an external CoverImage to the internal CoverImage DTO.
     *
     * @param external the cover image from the external provider
     * @param index    the positional index for ordering
     * @return the mapped internal CoverImage
     */
    public CoverImage toCoverImage(ExternalCoverImage external, int index) {
        if (external == null) {
            return null;
        }
        return new CoverImage(
                external.getUrl(),
                external.getWidth() != null ? external.getWidth() : 0,
                external.getHeight() != null ? external.getHeight() : 0,
                index
        );
    }

    /**
     * Selects the best series from a list of series entries returned by an external provider.
     * <p>
     * Heuristics:
     * <ol>
     *   <li>If only one series, use it.</li>
     *   <li>If multiple series and a book title is available, pick the series whose name
     *       has the highest similarity to the book title (case-insensitive substring match).</li>
     *   <li>Fallback: use the first series in the list (providers should order by relevance).</li>
     * </ol>
     */
    ExternalSeriesInfo pickBestSeries(List<ExternalSeriesInfo> seriesList, String bookTitle) {
        if (seriesList == null || seriesList.isEmpty()) {
            return null;
        }
        if (seriesList.size() == 1) {
            return seriesList.getFirst();
        }
        if (bookTitle != null && !bookTitle.isBlank()) {
            String titleLower = bookTitle.toLowerCase();
            ExternalSeriesInfo bestMatch = null;
            int bestScore = -1;

            for (ExternalSeriesInfo series : seriesList) {
                if (series.getName() == null) continue;
                int score = computeSeriesMatchScore(series.getName().toLowerCase(), titleLower);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = series;
                }
            }
            if (bestMatch != null) {
                return bestMatch;
            }
        }
        return seriesList.getFirst();
    }

    /**
     * Computes a simple match score between a series name and a book title.
     * Higher is better. Uses longest common substring length as the primary metric.
     */
    private int computeSeriesMatchScore(String seriesName, String title) {
        if (title.contains(seriesName)) {
            return seriesName.length() * 2;
        }
        if (seriesName.contains(title)) {
            return title.length();
        }
        // Longest common substring
        return longestCommonSubstringLength(seriesName, title);
    }

    private int longestCommonSubstringLength(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int maxLen = 0;
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    maxLen = Math.max(maxLen, dp[i][j]);
                }
            }
        }
        return maxLen;
    }

    /**
     * Parses a date string in ISO 8601 format (yyyy-MM-dd).
     * Returns null if the string is null, blank, or cannot be parsed.
     * Handles partial dates like "2010" or "2010-08" by padding to a full date.
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            String normalized = dateStr.trim();
            if (normalized.matches("\\d{4}")) {
                normalized = normalized + "-01-01";
            } else if (normalized.matches("\\d{4}-\\d{2}")) {
                normalized = normalized + "-01";
            }
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
