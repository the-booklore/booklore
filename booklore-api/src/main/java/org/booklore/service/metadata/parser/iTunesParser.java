package org.booklore.service.metadata.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.MetadataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class iTunesParser implements BookParser {

    private static final String ITUNES_SEARCH_API_URL = "https://itunes.apple.com/search";
    private static final long MIN_REQUEST_INTERVAL_MS = 1500;
    private static final int MAX_RESULTS = 20;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    @Autowired
    public iTunesParser(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newHttpClient());
    }

    public iTunesParser(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> fetchedBookMetadata = fetchMetadata(book, fetchMetadataRequest);
        return fetchedBookMetadata.isEmpty() ? null : fetchedBookMetadata.getFirst();
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        // 1. Try ISBN Search
        if (fetchMetadataRequest.getIsbn() != null && !fetchMetadataRequest.getIsbn().isBlank()) {
            List<BookMetadata> isbnResults = searchByIsbn(ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn()));
            if (!isbnResults.isEmpty()) {
                return isbnResults;
            }
            log.info("iTunes: ISBN search returned no results, falling back to title search");
        }

        String title = fetchMetadataRequest.getTitle();
        String author = fetchMetadataRequest.getAuthor();
        
        List<BookMetadata> results = Collections.emptyList();

        // 2. Try Title + Author
        if (title != null && !title.isBlank() && author != null && !author.isBlank()) {
            String searchTerm = title + " " + author;
            log.info("iTunes: Searching with Title + Author: {}", searchTerm);
            results = searchByTerm(searchTerm);
        }

        // 3. Try Title only
        if (results.isEmpty() && title != null && !title.isBlank()) {
            log.info("iTunes: Searching with Title only: {}", title);
            results = searchByTerm(title);
        }

        return results;
    }

    private List<BookMetadata> searchByIsbn(String isbn) {
        return searchByTerm(isbn);
    }

    private List<BookMetadata> searchByTerm(String term) {
        List<BookMetadata> ebookResults = fetchFromApi(term, "ebook");
        List<BookMetadata> audiobookResults = fetchFromApi(term, "audiobook");
        
        Map<String, BookMetadata> combined = new LinkedHashMap<>();
        ebookResults.forEach(m -> {
            if (m.getExternalUrl() != null) {
                combined.put(m.getExternalUrl(), m);
            }
        });
        audiobookResults.forEach(m -> {
            if (m.getExternalUrl() != null) {
                combined.put(m.getExternalUrl(), m);
            }
        });
        
        return new ArrayList<>(combined.values());
    }

    private List<BookMetadata> fetchFromApi(String query, String entity) {
        try {
            waitForRateLimit();

            URI uri = UriComponentsBuilder.fromUriString(ITUNES_SEARCH_API_URL)
                    .queryParam("term", query)
                    .queryParam("entity", entity)
                    .queryParam("limit", MAX_RESULTS)
                    .queryParam("country", "US")
                    .build()
                    .toUri();

            log.info("iTunes API URL: {}", uri);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return handleApiResponse(response);
        } catch (IOException e) {
            log.error("IO error while fetching metadata from iTunes API: {}", e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            log.error("Request to iTunes API was interrupted");
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private List<BookMetadata> handleApiResponse(HttpResponse<String> response) throws IOException {
        int statusCode = response.statusCode();
        
        if (statusCode == 200) {
            List<BookMetadata> results = parseITunesResponse(response.body());
            List<BookMetadata> filtered = filterIrrelevantResults(results);
            return sortByCompleteness(filtered);
        }
        
        if (statusCode == 429) {
            log.warn("iTunes API rate limit exceeded. Consider increasing request interval.");
            return List.of();
        }
        
        if (statusCode >= 500) {
            log.error("iTunes API server error. Status: {}", statusCode);
            return List.of();
        }
        
        log.error("iTunes API request failed. Status: {}, Response: {}", statusCode, response.body());
        return List.of();
    }

    private List<BookMetadata> parseITunesResponse(String responseBody) throws IOException {
        ITunesSearchResponse response = objectMapper.readValue(responseBody, ITunesSearchResponse.class);
        
        if (response == null || response.results == null || response.results.isEmpty()) {
            return List.of();
        }
        
        return response.results.stream()
                .map(this::convertToBookMetadata)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BookMetadata convertToBookMetadata(ITunesResult item) {
        try {
            Set<String> authors = new LinkedHashSet<>();
            if (item.artistName != null && !item.artistName.isBlank()) {
                authors.add(item.artistName.trim());
            }
            
            Set<String> categories = new LinkedHashSet<>();
            if (item.genres != null) {
                categories.addAll(item.genres.stream()
                        .filter(Objects::nonNull)
                        .filter(g -> !g.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toSet()));
            }
            
            return BookMetadata.builder()
                    .provider(MetadataProvider.iTunes)
                    .title(cleanText(item.trackName))
                    .description(cleanText(item.description))
                    .authors(authors)
                    .categories(categories)
                    .publishedDate(parseDate(item.releaseDate))
                    .thumbnailUrl(getBestArtworkUrl(item.artworkUrl100))
                    .externalUrl(item.trackViewUrl)
                    .build();
        } catch (Exception e) {
            log.error("Error converting iTunes result to BookMetadata: {}", e.getMessage());
            return null;
        }
    }

    private String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return WHITESPACE_PATTERN.matcher(text.trim()).replaceAll(" ");
    }

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        
        try {
            if (dateString.contains("T")) {
                dateString = dateString.substring(0, dateString.indexOf("T"));
            }
            return LocalDate.parse(dateString, ISO_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.debug("Could not parse date '{}': {}", dateString, e.getMessage());
            return null;
        }
    }

    private String getBestArtworkUrl(String artworkUrl) {
        if (artworkUrl == null || artworkUrl.isBlank()) {
            return null;
        }
        
        artworkUrl = artworkUrl.replace("100x100", "2400x2400");
        artworkUrl = artworkUrl.replace("http://", "https://");
        
        return artworkUrl;
    }

    private List<BookMetadata> filterIrrelevantResults(List<BookMetadata> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        
        return results.stream()
                .filter(this::isRelevantResult)
                .collect(Collectors.toList());
    }

    private boolean isRelevantResult(BookMetadata metadata) {
        if (metadata.getTitle() == null || metadata.getTitle().isBlank()) {
            return false;
        }
        
        if (metadata.getTitle().trim().length() < 2) {
            return false;
        }
        
        boolean hasAuthor = metadata.getAuthors() != null && !metadata.getAuthors().isEmpty();
        boolean hasDescription = metadata.getDescription() != null && metadata.getDescription().length() > 10;
        boolean hasUrl = metadata.getExternalUrl() != null && !metadata.getExternalUrl().isBlank();
        
        return hasAuthor || hasDescription || hasUrl;
    }

    private List<BookMetadata> sortByCompleteness(List<BookMetadata> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        
        return results.stream()
                .sorted((a, b) -> Integer.compare(
                        countPopulatedFields(b),
                        countPopulatedFields(a)))
                .collect(Collectors.toList());
    }

    private int countPopulatedFields(BookMetadata metadata) {
        int count = 0;
        
        if (metadata.getTitle() != null && !metadata.getTitle().isBlank()) count++;
        if (metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()) count++;
        if (metadata.getDescription() != null && !metadata.getDescription().isBlank()) count++;
        if (metadata.getPublishedDate() != null) count++;
        if (metadata.getCategories() != null && !metadata.getCategories().isEmpty()) count++;
        if (metadata.getThumbnailUrl() != null && !metadata.getThumbnailUrl().isBlank()) count++;
        if (metadata.getExternalUrl() != null && !metadata.getExternalUrl().isBlank()) count++;
        
        return count;
    }

    private void waitForRateLimit() {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime.get();
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime.set(System.currentTimeMillis());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ITunesSearchResponse {
        @JsonProperty("resultCount")
        public Integer resultCount;
        
        @JsonProperty("results")
        public List<ITunesResult> results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ITunesResult {
        @JsonProperty("trackId")
        public Long trackId;
        
        @JsonProperty("trackName")
        public String trackName;
        
        @JsonProperty("artistName")
        public String artistName;
        
        @JsonProperty("description")
        public String description;
        
        @JsonProperty("releaseDate")
        public String releaseDate;
        
        @JsonProperty("trackViewUrl")
        public String trackViewUrl;
        
        @JsonProperty("artworkUrl100")
        public String artworkUrl100;
        
        @JsonProperty("genres")
        public List<String> genres;
        
        @JsonProperty("averageUserRating")
        public Double averageUserRating;
        
        @JsonProperty("userRatingCount")
        public Integer userRatingCount;
    }
}