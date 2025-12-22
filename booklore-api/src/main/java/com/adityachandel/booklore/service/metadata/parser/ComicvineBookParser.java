package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.response.comicvineapi.Comic;
import com.adityachandel.booklore.model.dto.response.comicvineapi.ComicvineApiResponse;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComicvineBookParser implements BookParser {

    private static final String COMICVINE_URL = "https://comicvine.gamespot.com/api/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ObjectMapper objectMapper;
    private final AppSettingService appSettingService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

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
        String apiToken = getApiToken();
        if (apiToken == null) return Collections.emptyList();

        log.info("Comicvine: Fetching metadata for term: '{}'", term);
        try {
            String fieldsList = String.join(",", "api_detail_url", "cover_date", "description", "id", "image", "issue_number", "name", "publisher", "volume");
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "BookLore/1.0 (Book and Comic Metadata Fetcher; +https://github.com/booklore-app/booklore)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseComicvineApiResponse(response.body());
            } else {
                log.error("Comicvine Search API returned status code {}", response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error fetching metadata from Comicvine Search API", e);
        }
        return Collections.emptyList();
    }

    private String getSearchTerm(Book book, FetchMetadataRequest request) {
        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            return request.getTitle();
        } else if (book.getFileName() != null && !book.getFileName().isEmpty()) {
            return BookUtils.cleanFileName(book.getFileName());
        }
        return null;
    }

    private List<BookMetadata> parseComicvineApiResponse(String responseBody) throws IOException {
        ComicvineApiResponse apiResponse = objectMapper.readValue(responseBody, ComicvineApiResponse.class);
        if (apiResponse.getResults() == null) {
            return Collections.emptyList();
        }
        return apiResponse.getResults().stream()
                .map(this::convertToBookMetadata)
                .collect(Collectors.toList());
    }

    private BookMetadata convertToBookMetadata(Comic comic) {
        return BookMetadata.builder()
                .provider(MetadataProvider.Comicvine)
                .comicvineId(String.valueOf(comic.getId()))
                .title(comic.getName())
                .authors(new HashSet<>())
                .thumbnailUrl(comic.getImage() != null ? comic.getImage().getMediumUrl() : null)
                .description(comic.getDescription())
                .seriesName(comic.getVolume() != null ? comic.getVolume().getName() : null)
                .seriesNumber(safeParseFloat(comic.getIssueNumber()))
                .publishedDate(safeParseDate(comic.getCoverDate()))
                .build();
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

    /*public Set<String> fetchAuthors(int issueId) {
        String apiToken = getApiToken();
        if (apiToken == null) return Collections.emptySet();

        try {
            String fieldsList = String.join(",", "person_credits");

            URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                    .path("/issue/4000-" + issueId + "/")
                    .queryParam("api_key", apiToken)
                    .queryParam("format", "json")
                    .queryParam("field_list", fieldsList)
                    .build()
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "Booklore/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ComicvineIssueResponse issueResponse = objectMapper.readValue(response.body(), ComicvineIssueResponse.class);
                if (issueResponse.getResults() == null || issueResponse.getResults().getPersonCredits() == null) {
                    log.warn("No person credits found for issue ID {}", issueId);
                    return Collections.emptySet();
                }

                return issueResponse.getResults().getPersonCredits().stream()
                        .filter(pc -> pc.getRole() != null && pc.getRole().toLowerCase().contains("writer"))
                        .map(ComicvineIssueResponse.PersonCredit::getName)
                        .collect(Collectors.toSet());

            } else {
                log.error("Comicvine Issue API returned status code {}", response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error fetching issue metadata from Comicvine Issue API", e);
        }
        return Collections.emptySet();
    }*/
}