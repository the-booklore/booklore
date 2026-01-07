package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.response.comicvineapi.Comic;
import com.adityachandel.booklore.model.dto.response.comicvineapi.ComicvineApiResponse;
import com.adityachandel.booklore.model.dto.response.comicvineapi.ComicvineIssueResponse;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.BookUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComicvineBookParser implements BookParser {

    private static final String COMICVINE_URL = "https://comicvine.gamespot.com/api/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
    private static final Pattern SERIES_ISSUE_PATTERN = Pattern.compile("^(.*?\\s+#?(\\d+(?:\\.\\d+)?))(?:\\s+.*)?$");
    private static final long MIN_REQUEST_INTERVAL_MS = 1000; // 1 second

    private final ObjectMapper objectMapper;
    private final AppSettingService appSettingService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final AtomicBoolean rateLimited = new AtomicBoolean(false);
    private final AtomicLong rateLimitResetTime = new AtomicLong(0);
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        String searchTerm = getSearchTerm(book, fetchMetadataRequest);
        if (searchTerm == null) {
            log.warn("No valid search term provided for metadata fetch.");
            return Collections.emptyList();
        }
        return getMetadataListByTerm(searchTerm);
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> metadataList = fetchMetadata(book, fetchMetadataRequest);
        return metadataList.isEmpty() ? null : metadataList.getFirst();
    }

    public List<BookMetadata> getMetadataListByTerm(String term) {
        SeriesAndIssue seriesAndIssue = extractSeriesAndIssue(term);

        // If we have an issue number, try the precise Volume -> Issue workflow
        if (seriesAndIssue.issue() != null) {
            log.info("Attempting structured search for Series: '{}', Issue: '{}', Year: '{}'", 
                    seriesAndIssue.series(), seriesAndIssue.issue(), seriesAndIssue.year());
            List<BookMetadata> preciseResults = searchVolumesAndIssues(seriesAndIssue.series(), seriesAndIssue.issue(), seriesAndIssue.year());
            if (!preciseResults.isEmpty()) {
                return preciseResults;
            }
            log.info("Structured search yielded no results, falling back to general search.");
        }

        // Fallback to general search
        return searchGeneral(term);
    }

    private List<BookMetadata> searchVolumesAndIssues(String seriesName, String issueNumber, Integer extractedYear) {
        List<Comic> volumes = searchVolumes(seriesName);
        if (volumes.isEmpty()) {
            return Collections.emptyList();
        }

        // SCORING LOGIC
        // Sort volumes so the most likely candidate is first.
        volumes.sort((v1, v2) -> {
            int score1 = 0;
            int score2 = 0;

            // 1. Year Match is the strongest signal
            if (extractedYear != null) {
                if (matchesYear(v1, extractedYear)) score1 += 100;
                if (matchesYear(v2, extractedYear)) score2 += 100;
            }

            // 2. Exact Name Match
            if (v1.getName() != null && v1.getName().equalsIgnoreCase(seriesName)) score1 += 50;
            if (v2.getName() != null && v2.getName().equalsIgnoreCase(seriesName)) score2 += 50;

            // 3. Prioritize major publishers
            Set<String> majorPublishers = Set.of("Marvel", "DC Comics", "Image");
            if (v1.getPublisher() != null && majorPublishers.stream().anyMatch(p -> v1.getPublisher().getName().contains(p))) score1 += 10;
            if (v2.getPublisher() != null && majorPublishers.stream().anyMatch(p -> v2.getPublisher().getName().contains(p))) score2 += 10;

            return Integer.compare(score2, score1); // Descending
        });

        List<BookMetadata> results = new ArrayList<>();
        // Limit to top 3 volumes to minimize API calls
        int limit = Math.min(volumes.size(), 3);

        for (int i = 0; i < limit; i++) {
            Comic volume = volumes.get(i);
            
            List<BookMetadata> issues = searchIssuesInVolume(volume.getId(), issueNumber);
            if (!issues.isEmpty()) {
                results.addAll(issues);
                
                // If we found a result in a high-score volume (e.g. year matched), stop.
                if (extractedYear != null && matchesYear(volume, extractedYear)) {
                    log.info("Found match in year-aligned volume '{}' ({}) . Stopping further volume searches.", volume.getName(), volume.getStartYear());
                    break;
                }
            }
        }
        return results;
    }

    private boolean matchesYear(Comic volume, int targetYear) {
        if (volume.getStartYear() == null) return false;
        try {
            int volYear = Integer.parseInt(volume.getStartYear());
            // Allow strict match or match-within-1-year (publish dates can drift from cover dates)
            return Math.abs(volYear - targetYear) <= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private List<Comic> searchVolumes(String seriesName) {
        String apiToken = getApiToken();
        if (apiToken == null) return Collections.emptyList();

        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/search/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("resources", "volume")
                .queryParam("query", seriesName)
                .queryParam("limit", 10)
                .queryParam("field_list", "id,name,publisher,start_year,count_of_issues")
                .build()
                .toUri();

        ComicvineApiResponse response = sendRequest(uri, ComicvineApiResponse.class);
        return response != null && response.getResults() != null ? response.getResults() : Collections.emptyList();
    }

    private List<BookMetadata> searchIssuesInVolume(int volumeId, String issueNumber) {
        String apiToken = getApiToken();
        if (apiToken == null) return Collections.emptyList();

        // Get basic fields first - person_credits is unreliable in list endpoints
        String fieldsList = String.join(",", "api_detail_url", "cover_date", "description", "id", "image", "issue_number", "name", "volume", "site_detail_url");

        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/issues/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("filter", "volume:" + volumeId + ",issue_number:" + issueNumber)
                .queryParam("field_list", fieldsList)
                .queryParam("limit", 5)
                .build()
                .toUri();

        ComicvineApiResponse response = sendRequest(uri, ComicvineApiResponse.class);
        if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
            // ALWAYS fetch full details for the first/best match to get person_credits
            Comic firstIssue = response.getResults().getFirst();
            BookMetadata detailed = fetchIssueDetails(firstIssue.getId());
            
            if (detailed != null) {
                return Collections.singletonList(detailed);
            }
            // Fallback to basic data if detail fetch fails
            return Collections.singletonList(convertToBookMetadata(firstIssue));
        }
        return Collections.emptyList();
    }

    private BookMetadata fetchIssueDetails(int issueId) {
        String apiToken = getApiToken();
        if (apiToken == null) return null;

        String fieldsList = String.join(",", "api_detail_url", "cover_date", "description", "id", "issue_number", "name", "person_credits", "volume", "site_detail_url");

        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/issue/4000-" + issueId + "/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("field_list", fieldsList)
                .build()
                .toUri();

        ComicvineIssueResponse response = sendRequest(uri, ComicvineIssueResponse.class);
        if (response != null && response.getResults() != null) {
            return convertToBookMetadata(response.getResults());
        }
        return null;
    }

    private List<BookMetadata> searchGeneral(String term) {
        String apiToken = getApiToken();
        if (apiToken == null) return Collections.emptyList();

        String fieldsList = String.join(",", "api_detail_url", "cover_date", "description", "id", "image", "issue_number", "name", "publisher", "volume", "site_detail_url");
        String resources = "volume,issue";

        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/search/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("resources", resources)
                .queryParam("query", term)
                .queryParam("limit", 10)
                .queryParam("field_list", fieldsList)
                .build()
                .toUri();

        ComicvineApiResponse response = sendRequest(uri, ComicvineApiResponse.class);
        if (response != null && response.getResults() != null) {
            return response.getResults().stream()
                    .map(this::convertToBookMetadata)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private <T> T sendRequest(URI uri, Class<T> responseType) {
        if (rateLimited.get()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime < rateLimitResetTime.get()) {
                log.warn("ComicVine API is currently rate limited. Skipping request. Rate limit resets at: {}",
                        Instant.ofEpochMilli(rateLimitResetTime.get()));
                return null;
            } else {
                rateLimited.compareAndSet(true, false);
            }
        }

        // Enforce 1 req/sec limit
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime.get();
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest);
            } catch (InterruptedException ignored) {}
        }
        lastRequestTime.set(System.currentTimeMillis());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "BookLore/1.0 (Book and Comic Metadata Fetcher; +https://github.com/booklore-app/booklore)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), responseType);
            } else if (response.statusCode() == 420 || response.statusCode() == 429) {
                handleRateLimit(response);
            } else {
                log.error("Comicvine API returned status code {}", response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error fetching data from Comicvine API", e);
        }
        return null;
    }

    private void handleRateLimit(HttpResponse<String> response) {
        log.error("ComicVine API rate limit exceeded (Error {}). Setting rate limit flag.", response.statusCode());

        long resetDelayMs = 3600000; // Default to 1 hour
        List<String> retryAfterHeaders = response.headers().allValues("Retry-After");
        if (!retryAfterHeaders.isEmpty()) {
            try {
                String retryAfter = retryAfterHeaders.getFirst();
                if (DIGIT_PATTERN.matcher(retryAfter).matches()) {
                    resetDelayMs = Long.parseLong(retryAfter) * 1000;
                } else {
                    Instant instant = Instant.parse(retryAfter);
                    resetDelayMs = instant.toEpochMilli() - System.currentTimeMillis();
                    if (resetDelayMs <= 0) {
                        resetDelayMs = 3600000; // Default to 1 hour if date is in the past
                    }
                }
            } catch (Exception e) {
                log.warn("Could not parse Retry-After header '{}', using default 1 hour delay", retryAfterHeaders.getFirst());
            }
        }

        if (rateLimited.compareAndSet(false, true)) {
            rateLimitResetTime.set(System.currentTimeMillis() + resetDelayMs);
            log.info("Rate limit will reset at: {}", Instant.ofEpochMilli(rateLimitResetTime.get()));
        }
    }

    private BookMetadata convertToBookMetadata(Comic comic) {
        Set<String> authors = new HashSet<>();
        if (comic.getPersonCredits() != null) {
            authors = comic.getPersonCredits().stream()
                    .filter(pc -> {
                        String r = pc.getRole() != null ? pc.getRole().toLowerCase() : "";
                        return r.contains("writer") || r.contains("script") || r.contains("plot") || r.contains("creator") || r.contains("author");
                    })
                    .map(Comic.PersonCredit::getName)
                    .collect(Collectors.toSet());
        }

        return BookMetadata.builder()
                .provider(MetadataProvider.Comicvine)
                .comicvineId(String.valueOf(comic.getId()))
                .title(comic.getName())
                .authors(authors)
                .thumbnailUrl(comic.getImage() != null ? comic.getImage().getMediumUrl() : null)
                .description(comic.getDescription())
                .seriesName(comic.getVolume() != null ? comic.getVolume().getName() : null)
                .seriesNumber(safeParseFloat(comic.getIssueNumber()))
                .publishedDate(safeParseDate(comic.getCoverDate()))
                .externalUrl(comic.getSiteDetailUrl())
                .build();
    }

    private BookMetadata convertToBookMetadata(ComicvineIssueResponse.IssueResults issue) {
        // Extract ID from api_detail_url: "https://comicvine.gamespot.com/api/issue/4000-12345/"
        String comicvineId = null;
        if (issue.getApiDetailUrl() != null) {
            Matcher matcher = Pattern.compile("/(\\d+)/?$").matcher(issue.getApiDetailUrl());
            if (matcher.find()) {
                comicvineId = matcher.group(1);
            }
        }

        Set<String> authors = new HashSet<>();
        if (issue.getPersonCredits() != null) {
            authors = issue.getPersonCredits().stream()
                    .filter(pc -> {
                        String r = pc.getRole() != null ? pc.getRole().toLowerCase() : "";
                        return r.contains("writer") || r.contains("script") || r.contains("plot") || r.contains("creator") || r.contains("author");
                    })
                    .map(Comic.PersonCredit::getName)
                    .collect(Collectors.toSet());
        }

        return BookMetadata.builder()
                .provider(MetadataProvider.Comicvine)
                .comicvineId(comicvineId)
                .title(issue.getName() != null ? issue.getName() :
                       (issue.getVolume() != null ? issue.getVolume().getName() + " #" + issue.getIssueNumber() : null))
                .authors(authors)
                .thumbnailUrl(issue.getImage() != null ? issue.getImage().getMediumUrl() : null)
                .description(issue.getDescription())
                .seriesName(issue.getVolume() != null ? issue.getVolume().getName() : null)
                .seriesNumber(safeParseFloat(issue.getIssueNumber()))
                .publishedDate(safeParseDate(issue.getCoverDate()))
                .externalUrl(issue.getSiteDetailUrl())
                .build();
    }

    private String getSearchTerm(Book book, FetchMetadataRequest request) {
        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            return request.getTitle();
        } else if (book.getFileName() != null && !book.getFileName().isEmpty()) {
            return BookUtils.cleanFileName(book.getFileName());
        }
        return null;
    }

    private SeriesAndIssue extractSeriesAndIssue(String term) {
        // 1. Extract Year if present: "Batman (2011)" or "Batman 2011"
        Integer year = null;
        Matcher yearMatcher = Pattern.compile("\\(?(\\d{4})\\)?").matcher(term);
        if (yearMatcher.find()) {
            try {
                int y = Integer.parseInt(yearMatcher.group(1));
                // Basic sanity check for comics (1900 - Next Year)
                if (y > 1900 && y <= LocalDate.now().getYear() + 1) {
                    year = y;
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2. Remove year from term for cleaner series name search
        String cleaned = term.replaceAll("\\(?\\d{4}\\)?", "").trim();

        // 3. Extract Series and Issue
        Matcher matcher = SERIES_ISSUE_PATTERN.matcher(cleaned);
        if (matcher.matches()) {
            String series = matcher.group(1).trim();
            String issueNum = matcher.group(2);
            
            // Remove trailing "#" if it stuck to the series name
            if (series.endsWith("#")) {
                series = series.substring(0, series.length() - 1).trim();
            }

            try {
                // Keep decimal if present, otherwise just string (ComicVine supports 1.5 etc)
                return new SeriesAndIssue(series, issueNum, year);
            } catch (NumberFormatException e) {
                return new SeriesAndIssue(series, issueNum, year);
            }
        }
        
        return new SeriesAndIssue(cleaned, null, year);
    }

    private static LocalDate safeParseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date '{}'", dateStr);
            return null;
        }
    }

    private static Float safeParseFloat(String numStr) {
        if (numStr == null || numStr.isEmpty()) return null;
        try {
            return Float.valueOf(numStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getApiToken() {
        String apiToken = appSettingService.getAppSettings().getMetadataProviderSettings().getComicvine().getApiKey();
        if (apiToken == null || apiToken.isEmpty()) {
            log.warn("Comicvine API token not set");
            return null;
        }
        return apiToken;
    }

    private record SeriesAndIssue(String series, String issue, Integer year) {}
}