package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.response.comicvineapi.Comic;
import com.adityachandel.booklore.model.dto.response.comicvineapi.ComicvineApiResponse;
import com.adityachandel.booklore.model.dto.response.comicvineapi.ComicvineIssueResponse;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
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
    private static final Pattern SERIES_ISSUE_PATTERN = Pattern.compile("^(.+?)\\s+#?(\\d+(?:\\.\\d+)?)(?:\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITAL_PATTERN = Pattern.compile("\\(digital\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARENTHETICAL_PATTERN = Pattern.compile("\\([^)]*\\)");
    private static final Pattern BRACKETED_PATTERN = Pattern.compile("\\[[^\\]]*\\]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_ISSUE_PATTERN = Pattern.compile("(annual|special|one-?shot)\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\(?(\\d{4})\\)?");
    private static final Pattern COMICVINE_ID_PATTERN = Pattern.compile("/(\\d+)/?$");
    private static final long MIN_REQUEST_INTERVAL_MS = 2000;

    // Field lists to minimize API calls by getting all useful data in one request
    private static final String VOLUME_FIELDS = "id,name,publisher,start_year,count_of_issues,description,deck,image,site_detail_url,aliases,first_issue,last_issue";
    private static final String ISSUE_LIST_FIELDS = "api_detail_url,cover_date,store_date,description,deck,id,image,issue_number,name,volume,site_detail_url,aliases,person_credits";
    private static final String ISSUE_DETAIL_FIELDS = "api_detail_url,cover_date,store_date,description,deck,id,image,issue_number,name,person_credits,volume,site_detail_url,aliases,character_credits,team_credits,story_arc_credits,location_credits";
    private static final String SEARCH_FIELDS = "api_detail_url,cover_date,store_date,description,deck,id,image,issue_number,name,publisher,volume,site_detail_url,resource_type,start_year,count_of_issues,aliases,person_credits";
    private static final Pattern ISSUE_NUMBER_PATTERN = Pattern.compile("issue\\s*#?\\d+");
    private static final Pattern ID_FORMAT_PATTERN = Pattern.compile("\\d+-?\\d*");
    private static final Pattern TRAILING_SLASHES_PATTERN = Pattern.compile("/+$");
    private static final Pattern VOLUME_SUFFIX_PATTERN = Pattern.compile("\\s+Vol\\.?\\s*\\d+$");

    private final ObjectMapper objectMapper;
    private final AppSettingService appSettingService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final AtomicBoolean rateLimited = new AtomicBoolean(false);
    private final AtomicLong rateLimitResetTime = new AtomicLong(0);
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private final AtomicLong apiCallCounter = new AtomicLong(0);
    private final Map<String, CachedVolumes> volumeCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CachedVolumes {
        final List<Comic> volumes;
        final long timestamp;

        CachedVolumes(List<Comic> volumes) {
            this.volumes = volumes;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 600_000; // 10 minutes cache
        }
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        // 1. Try ISBN Search
        String isbn = ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn());
        if (isbn != null && !isbn.isEmpty()) {
            log.info("Comicvine: Searching by ISBN: {}", isbn);
            // ComicVine "search" endpoint with "query" parameter performs a general search.
            List<BookMetadata> results = searchGeneral(isbn);
            if (!results.isEmpty()) return results;
            log.info("Comicvine: ISBN search returned no results, falling back to Title/Term.");
        }

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

        // Strategy 1: Precise structured search if issue number is present (most efficient)
        if (seriesAndIssue.issue() != null) {
            log.info("Attempting structured search for Series: '{}', Issue: '{}', Year: '{}', Type: '{}'", 
                    seriesAndIssue.series(), seriesAndIssue.issue(), seriesAndIssue.year(), seriesAndIssue.issueType());
            List<BookMetadata> preciseResults = searchVolumesAndIssues(seriesAndIssue.series(), seriesAndIssue.issue(), seriesAndIssue.year());
            if (!preciseResults.isEmpty()) {
                return preciseResults;
            }
            log.info("Structured search yielded no results, trying alternative strategies.");
            
            // Strategy 2: Try with cleaned series name variations
            List<BookMetadata> alternativeResults = tryAlternativeSeriesNames(seriesAndIssue);
            if (!alternativeResults.isEmpty()) {
                return alternativeResults;
            }
        }

        // Strategy 3: General search (fallback)
        List<BookMetadata> results = searchGeneral(term);
        if (!results.isEmpty()) {
            return results;
        }

        // Strategy 4: If general search failed with structured match, try modified term
        if (seriesAndIssue.issue() != null && seriesAndIssue.remainder() != null && !seriesAndIssue.remainder().isBlank()) {
            String modifiedTerm = seriesAndIssue.series() + " " + seriesAndIssue.remainder();
            if (seriesAndIssue.year() != null) {
                modifiedTerm += " " + seriesAndIssue.year();
            }
            log.info("General search failed, trying modified term: '{}'", modifiedTerm);
            return searchGeneral(modifiedTerm);
        }

        return Collections.emptyList();
    }

    private List<BookMetadata> tryAlternativeSeriesNames(SeriesAndIssue original) {
        String series = original.series();
        List<String> alternatives = new ArrayList<>();
        
        // Try removing "The " prefix
        if (series.toLowerCase().startsWith("the ")) {
            alternatives.add(series.substring(4));
        }
        
        // Try adding "The " prefix
        if (!series.toLowerCase().startsWith("the ")) {
            alternatives.add("The " + series);
        }
        
        // Try replacing hyphens with colons and vice versa
        if (series.contains(" - ")) {
            alternatives.add(series.replace(" - ", ": "));
        }
        if (series.contains(": ")) {
            alternatives.add(series.replace(": ", " - "));
        }
        
        // Try removing common suffixes like "(2023)" or "Vol. X"
        String cleaned = VOLUME_SUFFIX_PATTERN.matcher(series).replaceAll("").trim();
        if (!cleaned.equals(series)) {
            alternatives.add(cleaned);
        }
        
        for (String altSeries : alternatives) {
            log.debug("Trying alternative series name: '{}'", altSeries);
            List<BookMetadata> results = searchVolumesAndIssues(altSeries, original.issue(), original.year());
            if (!results.isEmpty()) {
                return results;
            }
        }
        
        return Collections.emptyList();
    }

    private List<BookMetadata> searchVolumesAndIssues(String seriesName, String issueNumber, Integer extractedYear) {
        String normalizedIssue = normalizeIssueNumber(issueNumber);
        
        if (seriesName.endsWith(" " + issueNumber) || seriesName.endsWith(" " + normalizedIssue)) {
            seriesName = seriesName.replaceAll("\\s+" + Pattern.quote(issueNumber) + "$", "")
                                   .replaceAll("\\s+" + Pattern.quote(normalizedIssue) + "$", "")
                                   .trim();
            log.warn("Issue number found in series name, corrected to: '{}'", seriesName);
        }

        final String finalSeriesName = seriesName;
        log.debug("searchVolumesAndIssues: seriesName='{}', issueNumber='{}', year='{}'", finalSeriesName, issueNumber, extractedYear);
        
        List<Comic> volumes = searchVolumes(finalSeriesName);
        if (volumes.isEmpty()) {
            log.debug("No volumes found for series '{}'", finalSeriesName);
            return Collections.emptyList();
        }

        // Sort volumes by relevance using scoring
        volumes.sort((v1, v2) -> {
            int score1 = calculateVolumeScore(v1, finalSeriesName, normalizedIssue, extractedYear);
            int score2 = calculateVolumeScore(v2, finalSeriesName, normalizedIssue, extractedYear);
            return Integer.compare(score2, score1);
        });

        List<BookMetadata> results = new ArrayList<>();
        // Limit to top 3 volumes to minimize API calls
        int limit = Math.min(volumes.size(), 3);

        for (int i = 0; i < limit; i++) {
            Comic volume = volumes.get(i);
            log.debug("Checking volume: id='{}', name='{}', start_year='{}'", volume.getId(), volume.getName(), volume.getStartYear());
            
            List<BookMetadata> issues = searchIssuesInVolume(volume, issueNumber);
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

    private int calculateVolumeScore(Comic volume, String seriesName, String normalizedIssue, Integer extractedYear) {
        int score = 0;

        if (extractedYear != null && matchesYear(volume, extractedYear)) {
            score += 100;
        }

        if (volume.getName() != null && volume.getName().equalsIgnoreCase(seriesName)) {
            score += 50;
        } else if (volume.getName() != null && volume.getName().toLowerCase().contains(seriesName.toLowerCase())) {
            score += 25;
        }

        try {
            int requestedIssue = (int) Math.floor(Double.parseDouble(normalizedIssue));
            if (volume.getCountOfIssues() != null && volume.getCountOfIssues() >= requestedIssue) {
                score += 20;
            }
        } catch (NumberFormatException ignored) {}

        Set<String> majorPublishers = Set.of("Marvel", "DC Comics", "Image Comics", "Dark Horse Comics", "IDW Publishing", "Dynamite Entertainment", "BOOM! Studios", "Valiant Entertainment");
        if (volume.getPublisher() != null && volume.getPublisher().getName() != null) {
            if (majorPublishers.stream().anyMatch(p -> volume.getPublisher().getName().contains(p))) {
                score += 10;
            }
        }

        if (volume.getStartYear() != null) {
            try {
                int year = Integer.parseInt(volume.getStartYear());
                if (year >= 2000) score += 5;
                if (year >= 2010) score += 5;
                if (year >= 2020) score += 5;
            } catch (NumberFormatException ignored) {}
        }

        return score;
    }

    private boolean matchesYear(Comic volume, int targetYear) {
        if (volume.getStartYear() == null) return false;
        try {
            int volYear = Integer.parseInt(volume.getStartYear());
            return Math.abs(volYear - targetYear) <= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Search for volumes using the /volumes endpoint with filter (more efficient than /search)
     * Falls back to /search if /volumes returns no results
     */
    private List<Comic> searchVolumes(String seriesName) {
        String cacheKey = seriesName.toLowerCase();
        CachedVolumes cached = volumeCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached volume search for '{}'", seriesName);
            return cached.volumes;
        }

        String apiToken = getApiToken();
        if (apiToken == null) return Collections.emptyList();

        // First try using /volumes endpoint with name filter (more efficient, allows up to 100 results)
        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/volumes/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("filter", "name:" + seriesName)
                .queryParam("limit", 20)
                .queryParam("field_list", VOLUME_FIELDS)
                .queryParam("sort", "count_of_issues:desc")
                .build()
                .toUri();

        ComicvineApiResponse response = sendRequest(uri, ComicvineApiResponse.class);
        List<Comic> volumes = response != null && response.getResults() != null ? response.getResults() : Collections.emptyList();

        // If no results, try search endpoint as fallback
        if (volumes.isEmpty()) {
            log.debug("No volumes found via /volumes filter, trying /search for '{}'", seriesName);
            volumes = searchVolumesViaSearch(seriesName, apiToken);
        }

        if (!volumes.isEmpty()) {
            volumeCache.put(cacheKey, new CachedVolumes(volumes));
        } else if (seriesName.contains(" - ")) {
            String alternativeName = seriesName.replace(" - ", ": ");
            log.debug("No results for '{}', trying alternative name '{}'", seriesName, alternativeName);
            return searchVolumes(alternativeName);
        }

        return volumes;
    }

    private List<Comic> searchVolumesViaSearch(String seriesName, String apiToken) {
        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/search/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("resources", "volume")
                .queryParam("query", seriesName)
                .queryParam("limit", 10)
                .queryParam("field_list", VOLUME_FIELDS)
                .build()
                .toUri();

        ComicvineApiResponse response = sendRequest(uri, ComicvineApiResponse.class);
        return response != null && response.getResults() != null ? response.getResults() : Collections.emptyList();
    }

    /**
     * Search for issues in a specific volume. Now passes the full volume object to enrich metadata.
     */
    private List<BookMetadata> searchIssuesInVolume(Comic volume, String issueNumber) {
        String apiToken = getApiToken();
        if (apiToken == null) return Collections.emptyList();

        String normalizedIssue = normalizeIssueNumber(issueNumber);
        log.debug("searchIssuesInVolume: volumeId='{}', original='{}', normalized='{}'", 
                  volume.getId(), issueNumber, normalizedIssue);

        // Use /issues endpoint with filter - can return up to 100 results but we only need matching issues
        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/issues/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("filter", "volume:" + volume.getId() + ",issue_number:" + normalizedIssue)
                .queryParam("field_list", ISSUE_LIST_FIELDS)
                .queryParam("limit", 5)
                .build()
                .toUri();

        ComicvineApiResponse response = sendRequest(uri, ComicvineApiResponse.class);
        if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
            Comic firstIssue = response.getResults().getFirst();
            String returnedIssue = normalizeIssueNumber(firstIssue.getIssueNumber());
            
            if (!issueNumbersMatch(normalizedIssue, returnedIssue)) {
                log.warn("Issue number mismatch! Requested '{}', got '{}' from volume {}", 
                         normalizedIssue, returnedIssue, volume.getId());
                return Collections.emptyList();
            }

            if (firstIssue.getPersonCredits() != null && !firstIssue.getPersonCredits().isEmpty()) {
                log.debug("Issue {} has person_credits, using basic metadata (saving 1 API call)", firstIssue.getId());
                return Collections.singletonList(convertToBookMetadata(firstIssue, volume));
            }

            BookMetadata detailed = fetchIssueDetails(firstIssue.getId(), volume);

            return Collections.singletonList(Objects.requireNonNullElseGet(detailed, () -> convertToBookMetadata(firstIssue, volume)));
        }
        return Collections.emptyList();
    }


    private BookMetadata fetchIssueDetails(int issueId, Comic volumeContext) {
        String apiToken = getApiToken();
        if (apiToken == null) return null;

        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/issue/4000-" + issueId + "/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("field_list", ISSUE_DETAIL_FIELDS)
                .build()
                .toUri();

        ComicvineIssueResponse response = sendRequest(uri, ComicvineIssueResponse.class);
        if (response != null && response.getResults() != null) {
            return convertToBookMetadata(response.getResults(), volumeContext);
        }
        return null;
    }

    private List<BookMetadata> searchGeneral(String term) {
        String apiToken = getApiToken();
        if (apiToken == null) return Collections.emptyList();

        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/search/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("resources", "volume,issue")
                .queryParam("query", term)
                .queryParam("limit", 10)
                .queryParam("field_list", SEARCH_FIELDS)
                .build()
                .toUri();

        ComicvineApiResponse response = sendRequest(uri, ComicvineApiResponse.class);
        if (response != null && response.getResults() != null) {
            return response.getResults().stream()
                    .map(comic -> convertToBookMetadata(comic, null))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private <T> T sendRequest(URI uri, Class<T> responseType) {
        return sendRequestWithRetry(uri, responseType, 2);
    }

    private <T> T sendRequestWithRetry(URI uri, Class<T> responseType, int retriesLeft) {
        if (rateLimited.get()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime < rateLimitResetTime.get()) {
                log.warn("ComicVine API is currently rate limited. Skipping request. Rate limit resets at: {}",
                        Instant.ofEpochMilli(rateLimitResetTime.get()));
                return null;
            } else {
                rateLimited.compareAndSet(true, false);
                log.info("ComicVine rate limit period expired, resuming normal requests");
            }
        }

        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime.get();
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            long sleepTime = MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest;
            log.debug("Rate limiting: sleeping {}ms before next request", sleepTime);
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime.set(System.currentTimeMillis());
        
        long callNumber = apiCallCounter.incrementAndGet();
        String endpoint = extractEndpointFromUri(uri);

        try {
            log.debug("ComicVine API call #{} to {}", callNumber, endpoint);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "BookLore/1.0 (Book and Comic Metadata Fetcher; +https://github.com/booklore-app/booklore)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("ComicVine API call #{} completed: status={}, size={}bytes", 
                    callNumber, response.statusCode(), response.body() != null ? response.body().length() : 0);

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), responseType);
            } else if (response.statusCode() == 420 || response.statusCode() == 429) {
                handleRateLimit(response);
                return null;
            } else if (response.statusCode() >= 500 && retriesLeft > 0) {
                log.warn("ComicVine API returned status {}. Retrying... ({} retries left)", 
                         response.statusCode(), retriesLeft);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
                return sendRequestWithRetry(uri, responseType, retriesLeft - 1);
            } else {
                log.error("Comicvine API returned status code {}. Body: {}", response.statusCode(), response.body());
            }
        } catch (IOException e) {
            if (retriesLeft > 0) {
                log.warn("IOException during ComicVine request. Retrying... ({} retries left)", retriesLeft, e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
                return sendRequestWithRetry(uri, responseType, retriesLeft - 1);
            } else {
                log.error("Error fetching data from Comicvine API after retries", e);
            }
        } catch (InterruptedException e) {
            log.error("Request interrupted", e);
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private void handleRateLimit(HttpResponse<String> response) {
        log.error("ComicVine API rate limit exceeded (Error {}). Setting rate limit flag.", response.statusCode());

        long resetDelayMs = 3600000;
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
                        resetDelayMs = 3600000;
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


    private String extractEndpointFromUri(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) return "unknown";
        
        path = TRAILING_SLASHES_PATTERN.matcher(path).replaceAll("");
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            String segment = path.substring(lastSlash + 1);
            // If segment looks like an ID (4000-12345), include the parent
            if (ID_FORMAT_PATTERN.matcher(segment).matches()) {
                int prevSlash = path.lastIndexOf('/', lastSlash - 1);
                if (prevSlash >= 0) {
                    return path.substring(prevSlash);
                }
            }
            return "/" + segment;
        }
        return path;
    }

    private BookMetadata convertToBookMetadata(Comic comic, Comic volumeContext) {
        // Check if this is a Volume (from search results)
        if ("volume".equalsIgnoreCase(comic.getResourceType())) {
            return buildVolumeMetadata(comic);
        }

        // Get publisher from volume context if available
        String publisher = null;
        Integer seriesTotal = null;
        if (volumeContext != null) {
            if (volumeContext.getPublisher() != null) {
                publisher = volumeContext.getPublisher().getName();
            }
            seriesTotal = volumeContext.getCountOfIssues();
        }

        Set<String> authors = extractAuthors(comic.getPersonCredits());
        Set<String> tags = extractTags(comic);
        String formattedTitle = formatTitle(comic.getVolume() != null ? comic.getVolume().getName() : null, comic.getIssueNumber(), comic.getName());
        
        String dateToUse = comic.getStoreDate() != null ? comic.getStoreDate() : comic.getCoverDate();
        
        String description = comic.getDescription();
        if ((description == null || description.isEmpty()) && comic.getDeck() != null) {
            description = comic.getDeck();
        }

        BookMetadata metadata = BookMetadata.builder()
                .provider(MetadataProvider.Comicvine)
                .comicvineId(String.valueOf(comic.getId()))
                .title(formattedTitle)
                .authors(authors)
                .tags(tags.isEmpty() ? null : tags)
                .thumbnailUrl(comic.getImage() != null ? comic.getImage().getMediumUrl() : null)
                .description(description)
                .seriesName(comic.getVolume() != null ? comic.getVolume().getName() : null)
                .seriesNumber(safeParseFloat(comic.getIssueNumber()))
                .seriesTotal(seriesTotal)
                .publisher(publisher)
                .publishedDate(safeParseDate(dateToUse))
                .externalUrl(comic.getSiteDetailUrl())
                .build();
        
        if (metadata.getSeriesName() == null || metadata.getSeriesNumber() == null) {
            log.warn("Incomplete metadata for issue {}: missing series name or number", metadata.getComicvineId());
        }
        
        if (metadata.getAuthors().isEmpty()) {
            log.debug("No authors found for issue {} ({})", metadata.getComicvineId(), metadata.getTitle());
        }
        
        return metadata;
    }

    private BookMetadata buildVolumeMetadata(Comic volume) {
        Set<String> authors = extractAuthors(volume.getPersonCredits());
        
        return BookMetadata.builder()
                .provider(MetadataProvider.Comicvine)
                .comicvineId(String.valueOf(volume.getId()))
                .title(volume.getName())
                .seriesName(volume.getName())
                .seriesTotal(volume.getCountOfIssues())
                .publishedDate(safeParseDate(volume.getStartYear() + "-01-01"))
                .description(volume.getDescription() != null ? volume.getDescription() : volume.getDeck())
                .publisher(volume.getPublisher() != null ? volume.getPublisher().getName() : null)
                .thumbnailUrl(volume.getImage() != null ? volume.getImage().getMediumUrl() : null)
                .externalUrl(volume.getSiteDetailUrl())
                .authors(authors.isEmpty() ? null : authors)
                .build();
    }

    private BookMetadata convertToBookMetadata(ComicvineIssueResponse.IssueResults issue, Comic volumeContext) {
        // Extract ID from api_detail_url: "https://comicvine.gamespot.com/api/issue/4000-12345/"
        String comicvineId = null;
        if (issue.getApiDetailUrl() != null) {
            Matcher matcher = COMICVINE_ID_PATTERN.matcher(issue.getApiDetailUrl());
            if (matcher.find()) {
                comicvineId = matcher.group(1);
            }
        }

        // Get publisher and series total from volume context
        String publisher = null;
        Integer seriesTotal = null;
        if (volumeContext != null) {
            if (volumeContext.getPublisher() != null) {
                publisher = volumeContext.getPublisher().getName();
            }
            seriesTotal = volumeContext.getCountOfIssues();
        }

        Set<String> authors = extractAuthors(issue.getPersonCredits());
        Set<String> tags = extractTagsFromIssue(issue);
        String formattedTitle = formatTitle(issue.getVolume() != null ? issue.getVolume().getName() : null, issue.getIssueNumber(), issue.getName());

        // Prefer store_date (actual release date) over cover_date (printed date)
        String dateToUse = issue.getStoreDate() != null ? issue.getStoreDate() : issue.getCoverDate();
        
        // Use deck (brief summary) if description is very long or missing
        String description = issue.getDescription();
        if ((description == null || description.isEmpty()) && issue.getDeck() != null) {
            description = issue.getDeck();
        }

        BookMetadata metadata = BookMetadata.builder()
                .provider(MetadataProvider.Comicvine)
                .comicvineId(comicvineId)
                .title(formattedTitle)
                .authors(authors)
                .tags(tags.isEmpty() ? null : tags)
                .thumbnailUrl(issue.getImage() != null ? issue.getImage().getMediumUrl() : null)
                .description(description)
                .seriesName(issue.getVolume() != null ? issue.getVolume().getName() : null)
                .seriesNumber(safeParseFloat(issue.getIssueNumber()))
                .seriesTotal(seriesTotal)
                .publisher(publisher)
                .publishedDate(safeParseDate(dateToUse))
                .externalUrl(issue.getSiteDetailUrl())
                .build();
        
        if (metadata.getSeriesName() == null || metadata.getSeriesNumber() == null) {
            log.warn("Incomplete metadata for issue {}: missing series name or number", comicvineId);
        }
        
        if (metadata.getAuthors().isEmpty()) {
            log.debug("No authors found for issue {} ({})", comicvineId, metadata.getTitle());
        }
        
        return metadata;
    }

    private Set<String> extractTags(Comic comic) {
        Set<String> tags = new LinkedHashSet<>();
        
        if (comic.getStoryArcCredits() != null) {
            comic.getStoryArcCredits().stream()
                    .map(Comic.StoryArcCredit::getName)
                    .filter(name -> name != null && !name.isEmpty())
                    .limit(5) // Limit to top 5 story arcs
                    .forEach(tags::add);
        }
        
        if (comic.getCharacterCredits() != null) {
            comic.getCharacterCredits().stream()
                    .map(Comic.CharacterCredit::getName)
                    .filter(name -> name != null && !name.isEmpty())
                    .limit(10) // Limit to top 10 characters
                    .forEach(tags::add);
        }
        
        if (comic.getTeamCredits() != null) {
            comic.getTeamCredits().stream()
                    .map(Comic.TeamCredit::getName)
                    .filter(name -> name != null && !name.isEmpty())
                    .limit(5) // Limit to top 5 teams
                    .forEach(tags::add);
        }
        
        return tags;
    }

    private Set<String> extractTagsFromIssue(ComicvineIssueResponse.IssueResults issue) {
        Set<String> tags = new LinkedHashSet<>();
        
        if (issue.getStoryArcCredits() != null) {
            issue.getStoryArcCredits().stream()
                    .map(Comic.StoryArcCredit::getName)
                    .filter(name -> name != null && !name.isEmpty())
                    .limit(5)
                    .forEach(tags::add);
        }
        
        if (issue.getCharacterCredits() != null) {
            issue.getCharacterCredits().stream()
                    .map(Comic.CharacterCredit::getName)
                    .filter(name -> name != null && !name.isEmpty())
                    .limit(10)
                    .forEach(tags::add);
        }
        
        if (issue.getTeamCredits() != null) {
            issue.getTeamCredits().stream()
                    .map(Comic.TeamCredit::getName)
                    .filter(name -> name != null && !name.isEmpty())
                    .limit(5)
                    .forEach(tags::add);
        }
        
        return tags;
    }
    
    private String formatTitle(String seriesName, String issueNumber, String issueName) {
        if (seriesName == null) return issueName;
        
        String normalizedIssue = normalizeIssueNumber(issueNumber);
        String title = seriesName + " #" + (normalizedIssue != null ? normalizedIssue : "");
        
        // Append issue title if it exists and isn't just "Issue #X" or identical to series
        if (issueName != null && !issueName.isBlank()) {
            String lowerName = issueName.toLowerCase();
            boolean isGeneric = ISSUE_NUMBER_PATTERN.matcher(lowerName).matches() || lowerName.equals(seriesName.toLowerCase());
            if (!isGeneric) {
                title += " - " + issueName;
            }
        }
        return title;
    }
    
    private Set<String> extractAuthors(List<Comic.PersonCredit> personCredits) {
        if (personCredits == null || personCredits.isEmpty()) {
            return Collections.emptySet();
        }
        
        // Primary writer roles
        Set<String> writerRoles = Set.of("writer", "script", "story", "plotter", "plot");
        
        // Collect writers
        Set<String> authors = personCredits.stream()
                .filter(pc -> {
                    if (pc.getRole() == null) return false;
                    String role = pc.getRole().toLowerCase();
                    return writerRoles.stream().anyMatch(role::contains);
                })
                .map(Comic.PersonCredit::getName)
                .filter(name -> name != null && !name.isEmpty())
                .collect(Collectors.toSet());
        
        // Fallback: If no writers found, check for "creator" role
        if (authors.isEmpty()) {
            // DEBUG: Log all roles if we missed them
            List<String> allRoles = personCredits.stream()
                    .map(pc -> pc.getName() + " (" + pc.getRole() + ")")
                    .toList();
            log.debug("No writers found. Available roles: {}", allRoles);

            authors = personCredits.stream()
                    .filter(pc -> pc.getRole() != null && pc.getRole().toLowerCase().contains("creator"))
                    .map(Comic.PersonCredit::getName)
                    .filter(name -> name != null && !name.isEmpty())
                    .collect(Collectors.toSet());
        }
        
        return authors;
    }

    private String getSearchTerm(Book book, FetchMetadataRequest request) {
        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            return request.getTitle();
        } else if (book.getPrimaryFile() != null && book.getPrimaryFile().getFileName() != null && !book.getPrimaryFile().getFileName().isEmpty()) {
            // Use filename but preserve parentheses for year extraction
            String name = book.getPrimaryFile().getFileName();
            // Remove extension only
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                name = name.substring(0, dotIndex);
            }
            return name.trim();
        }
        return null;
    }

    private SeriesAndIssue extractSeriesAndIssue(String term) {
        // 1. Extract Year
        Integer year = null;
        Matcher yearMatcher = YEAR_PATTERN.matcher(term);
        String yearString = null;
        if (yearMatcher.find()) {
            try {
                int y = Integer.parseInt(yearMatcher.group(1));
                if (y > 1900 && y <= LocalDate.now().getYear() + 1) {
                    year = y;
                    yearString = yearMatcher.group(0);
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2. Remove ONLY the found year and noise from the term
        String cleaned = term;
        if (yearString != null) {
            cleaned = cleaned.replace(yearString, "");
        }
        cleaned = DIGITAL_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = PARENTHETICAL_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = BRACKETED_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.trim();
        cleaned = WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ");

        log.debug("Cleaned filename: '{}'", cleaned);

        // 3. Check for special issue keywords BEFORE extracting issue number
        String lowerCleaned = cleaned.toLowerCase();
        if (lowerCleaned.contains("annual") || 
            lowerCleaned.contains("special") || 
            lowerCleaned.contains("one-shot") ||
            lowerCleaned.contains("one shot")) {
            
            // Try to extract number from "Annual 2" or "Special 3"
            Matcher specialMatcher = SPECIAL_ISSUE_PATTERN.matcher(cleaned);
            if (specialMatcher.find()) {
                String type = specialMatcher.group(1);
                String num = specialMatcher.group(2);
                // Return series name before the special keyword
                String series = cleaned.substring(0, specialMatcher.start()).trim();
                return new SeriesAndIssue(series, num, year, type, null);
            } else {
                // "Batman Annual" without number - search for all annuals
                return new SeriesAndIssue(cleaned, null, year, "annual", null);
            }
        }

        // 4. Standard issue extraction with IMPROVED PATTERN
        Matcher matcher = SERIES_ISSUE_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            String series = matcher.group(1).trim();
            String issueNum = matcher.group(2);
            
            if (series.endsWith("#")) {
                series = series.substring(0, series.length() - 1).trim();
            }

            String remainder = cleaned.substring(matcher.end()).trim();

            log.debug("Extracted - Series: '{}', Issue: '{}', Remainder: '{}'", series, issueNum, remainder);
            return new SeriesAndIssue(series, issueNum, year, null, remainder);
        }
        
        log.debug("No issue number found in: '{}'", cleaned);
        return new SeriesAndIssue(cleaned, null, year, null, null);
    }

    /**
     * Lenient comparison of issue numbers.
     * Handles edge cases like:
     * - "1" vs "01" vs "1.0" (should match)
     * - "1.5" vs "1.50" (should match)
     * - "1AU" vs "1AU" (exact match for non-numeric)
     * - null handling
     */
    private boolean issueNumbersMatch(String requested, String returned) {
        if (requested == null || returned == null) {
            return requested == null && returned == null;
        }
        
        if (requested.equals(returned)) {
            return true;
        }
        
        try {
            double reqNum = Double.parseDouble(requested);
            double retNum = Double.parseDouble(returned);
            return Math.abs(reqNum - retNum) < 0.0001;
        } catch (NumberFormatException e) {
            return requested.equalsIgnoreCase(returned);
        }
    }
    
    private String normalizeIssueNumber(String issueNumber) {
        if (issueNumber == null || issueNumber.isEmpty()) {
            return issueNumber;
        }
        
        issueNumber = issueNumber.trim();
        
        if (issueNumber.contains("/")) {
            try {
                String[] parts = issueNumber.split("/");
                if (parts.length == 2) {
                    double numerator = Double.parseDouble(parts[0]);
                    double denominator = Double.parseDouble(parts[1]);
                    return String.valueOf(numerator / denominator);
                }
            } catch (NumberFormatException e) {
                log.warn("Could not parse fractional issue number '{}'", issueNumber);
                return issueNumber;
            }
        }
        
        try {
            if (issueNumber.contains(".")) {
                double d = Double.parseDouble(issueNumber);
                if (d == Math.floor(d)) {
                    return String.valueOf((int) d);
                } else {
                    return new java.text.DecimalFormat("0.#####").format(d);
                }
            } else {
                return String.valueOf(Integer.parseInt(issueNumber));
            }
        } catch (NumberFormatException e) {
            log.debug("Non-numeric issue number '{}', using as-is", issueNumber);
            return issueNumber;
        }
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

    private record SeriesAndIssue(String series, String issue, Integer year, String issueType, String remainder) {}
}