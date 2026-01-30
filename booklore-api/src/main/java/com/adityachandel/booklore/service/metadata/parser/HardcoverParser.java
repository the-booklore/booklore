package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.metadata.parser.hardcover.GraphQLResponse;
import com.adityachandel.booklore.service.metadata.parser.hardcover.HardcoverBookDetails;
import com.adityachandel.booklore.service.metadata.parser.hardcover.HardcoverBookSearchService;
import com.adityachandel.booklore.service.metadata.parser.hardcover.HardcoverMoodFilter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.WordUtils;
import org.apache.commons.text.similarity.FuzzyScore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class HardcoverParser implements BookParser {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final double AUTHOR_MATCH_THRESHOLD = 0.5;

    private final HardcoverBookSearchService hardcoverBookSearchService;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        String isbnCleaned = ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn());
        boolean searchByIsbn = isbnCleaned != null && !isbnCleaned.isBlank();

        if (searchByIsbn) {
            log.info("Hardcover: Fetching metadata using ISBN {}", fetchMetadataRequest.getIsbn());
            List<GraphQLResponse.Hit> hits = hardcoverBookSearchService.searchBooks(fetchMetadataRequest.getIsbn());
            return processHits(hits, fetchMetadataRequest, true);
        }

        String title = fetchMetadataRequest.getTitle();
        String author = fetchMetadataRequest.getAuthor();

        if (title == null || title.isBlank()) {
            log.warn("Hardcover: No title provided for search");
            return Collections.emptyList();
        }

        List<BookMetadata> results = Collections.emptyList();

        // 1. Try Title + Author
        if (author != null && !author.isBlank()) {
            String combinedQuery = title.trim() + " " + author.trim();
            log.info("Hardcover: Searching with title+author: '{}'", combinedQuery);
            List<GraphQLResponse.Hit> hits = hardcoverBookSearchService.searchBooks(combinedQuery);
            results = processHits(hits, fetchMetadataRequest, false);
        }

        // 2. If no valid results found (or no author provided), Try Title only
        if (results.isEmpty()) {
            log.info("Hardcover: Searching with title only: '{}'", title);
            List<GraphQLResponse.Hit> hits = hardcoverBookSearchService.searchBooks(title.trim());
            results = processHits(hits, fetchMetadataRequest, false);
        }

        if (results.isEmpty()) {
            log.info("Hardcover: No results found for title '{}'", title);
        }

        return results;
    }

    private List<BookMetadata> processHits(List<GraphQLResponse.Hit> hits, FetchMetadataRequest request, boolean searchByIsbn) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }

        FuzzyScore fuzzyScore = new FuzzyScore(Locale.ENGLISH);
        String searchAuthor = request.getAuthor() != null ? request.getAuthor() : "";

        // Filter by author
        List<GraphQLResponse.Document> matchedDocs = hits.stream()
                .map(GraphQLResponse.Hit::getDocument)
                .filter(doc -> filterByAuthor(doc, searchAuthor, searchByIsbn, fuzzyScore))
                .toList();

        if (matchedDocs.isEmpty()) {
            return Collections.emptyList();
        }

        // Only fetch detailed mood data for the TOP match to minimize API calls
        List<BookMetadata> results = new ArrayList<>();
        boolean isFirst = true;

        for (GraphQLResponse.Document doc : matchedDocs) {
            BookMetadata metadata = mapDocumentToMetadata(doc, request, isFirst);
            results.add(metadata);
            isFirst = false;
        }

        return results;
    }

    private boolean filterByAuthor(GraphQLResponse.Document doc, String searchAuthor, 
                                   boolean searchByIsbn, FuzzyScore fuzzyScore) {
        // Skip author filtering for ISBN searches or when no author provided
        if (searchByIsbn || searchAuthor.isBlank()) {
            return true;
        }

        if (doc.getAuthorNames() == null || doc.getAuthorNames().isEmpty()) {
            return false;
        }

        List<String> actualAuthorTokens = doc.getAuthorNames().stream()
                .map(String::toLowerCase)
                .flatMap(WHITESPACE_PATTERN::splitAsStream)
                .toList();
        List<String> searchAuthorTokens = List.of(WHITESPACE_PATTERN.split(searchAuthor.toLowerCase()));

        for (String actual : actualAuthorTokens) {
            for (String query : searchAuthorTokens) {
                int score = fuzzyScore.fuzzyScore(actual, query);
                int maxScore = Math.max(
                        fuzzyScore.fuzzyScore(query, query), 
                        fuzzyScore.fuzzyScore(actual, actual)
                );
                double similarity = maxScore > 0 ? (double) score / maxScore : 0;
                if (similarity >= AUTHOR_MATCH_THRESHOLD) {
                    return true;
                }
            }
        }
        return false;
    }

    private BookMetadata mapDocumentToMetadata(GraphQLResponse.Document doc, FetchMetadataRequest request, boolean fetchDetailedMoods) {
        BookMetadata metadata = new BookMetadata();
        metadata.setHardcoverId(doc.getSlug());

        Integer bookId = parseBookId(doc.getId());
        if (bookId != null) {
            metadata.setHardcoverBookId(bookId);
        }

        metadata.setTitle(doc.getTitle());
        metadata.setSubtitle(doc.getSubtitle());
        metadata.setDescription(doc.getDescription());

        if (doc.getAuthorNames() != null) {
            metadata.setAuthors(Set.copyOf(doc.getAuthorNames()));
        }

        mapSeriesInfo(doc, metadata);

        if (doc.getRating() != null) {
            metadata.setHardcoverRating(
                    BigDecimal.valueOf(doc.getRating()).setScale(2, RoundingMode.HALF_UP).doubleValue()
            );
        }
        metadata.setHardcoverReviewCount(doc.getRatingsCount());
        metadata.setPageCount(doc.getPages());

        if (doc.getReleaseDate() != null) {
            try {
                metadata.setPublishedDate(LocalDate.parse(doc.getReleaseDate()));
            } catch (Exception e) {
                log.debug("Could not parse release date: {}", doc.getReleaseDate());
            }
        }

        mapTagsAndMoods(doc, metadata, bookId, fetchDetailedMoods);
        mapIsbns(doc, request, metadata);

        metadata.setThumbnailUrl(doc.getImage() != null ? doc.getImage().getUrl() : null);
        metadata.setProvider(MetadataProvider.Hardcover);

        return metadata;
    }

    private Integer parseBookId(String id) {
        if (id == null) {
            return null;
        }
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            log.debug("Could not parse Hardcover book ID: {}", id);
            return null;
        }
    }

    private void mapSeriesInfo(GraphQLResponse.Document doc, BookMetadata metadata) {
        if (doc.getFeaturedSeries() == null) {
            return;
        }
        if (doc.getFeaturedSeries().getSeries() != null) {
            metadata.setSeriesName(doc.getFeaturedSeries().getSeries().getName());
            metadata.setSeriesTotal(doc.getFeaturedSeries().getSeries().getBooksCount());
        }
        if (doc.getFeaturedSeries().getPosition() != null) {
            try {
                metadata.setSeriesNumber(Float.parseFloat(String.valueOf(doc.getFeaturedSeries().getPosition())));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void mapTagsAndMoods(GraphQLResponse.Document doc, BookMetadata metadata, Integer bookId, boolean fetchDetailedMoods) {
        boolean usedDetailedMoods = false;

        if (fetchDetailedMoods && bookId != null) {
            usedDetailedMoods = tryFetchDetailedMoods(bookId, metadata);
        }

        if (!usedDetailedMoods && doc.getMoods() != null && !doc.getMoods().isEmpty()) {
            Set<String> basicFilteredMoods = HardcoverMoodFilter.filterBasicMoods(doc.getMoods());
            metadata.setMoods(basicFilteredMoods.stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        if ((metadata.getCategories() == null || metadata.getCategories().isEmpty())
                && doc.getGenres() != null && !doc.getGenres().isEmpty()) {
            metadata.setCategories(doc.getGenres().stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toSet()));
        }

        if ((metadata.getTags() == null || metadata.getTags().isEmpty())
                && doc.getTags() != null && !doc.getTags().isEmpty()) {
            metadata.setTags(doc.getTags().stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toSet()));
        }
    }

    private boolean tryFetchDetailedMoods(Integer bookId, BookMetadata metadata) {
        try {
            HardcoverBookDetails details = hardcoverBookSearchService.fetchBookDetails(bookId);
            if (details == null || details.getCachedTags() == null || details.getCachedTags().isEmpty()) {
                return false;
            }

            Set<String> filteredMoods = HardcoverMoodFilter.filterMoodsWithCounts(details.getCachedTags());
            if (!filteredMoods.isEmpty()) {
                metadata.setMoods(filteredMoods.stream()
                        .map(WordUtils::capitalizeFully)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
            }

            Set<String> filteredGenres = HardcoverMoodFilter.filterGenresWithCounts(details.getCachedTags());
            if (!filteredGenres.isEmpty()) {
                metadata.setCategories(filteredGenres.stream()
                        .map(WordUtils::capitalizeFully)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
            }

            Set<String> filteredTags = HardcoverMoodFilter.filterTagsWithCounts(details.getCachedTags());
            if (!filteredTags.isEmpty()) {
                metadata.setTags(filteredTags.stream()
                        .map(WordUtils::capitalizeFully)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
            }

            return !filteredMoods.isEmpty();
        } catch (Exception e) {
            log.debug("Failed to fetch book details: {}", e.getMessage());
            return false;
        }
    }

    private void mapIsbns(GraphQLResponse.Document doc, FetchMetadataRequest request, BookMetadata metadata) {
        if (doc.getIsbns() == null) {
            return;
        }

        String inputIsbn = request.getIsbn();

        if (inputIsbn != null && inputIsbn.length() == 10 && doc.getIsbns().contains(inputIsbn)) {
            metadata.setIsbn10(inputIsbn);
        } else {
            metadata.setIsbn10(doc.getIsbns().stream()
                    .filter(isbn -> isbn.length() == 10)
                    .findFirst()
                    .orElse(null));
        }

        if (inputIsbn != null && inputIsbn.length() == 13 && doc.getIsbns().contains(inputIsbn)) {
            metadata.setIsbn13(inputIsbn);
        } else {
            metadata.setIsbn13(doc.getIsbns().stream()
                    .filter(isbn -> isbn.length() == 13)
                    .findFirst()
                    .orElse(null));
        }
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> bookMetadata = fetchMetadata(book, fetchMetadataRequest);
        return bookMetadata.isEmpty() ? null : bookMetadata.getFirst();
    }
}
