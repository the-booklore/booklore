package com.adityachandel.booklore.service.metadata.parser.itunes;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class iTunesApiService {
    
    private static final String ITUNES_SEARCH_API_URL = "https://itunes.apple.com/search";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    
    private final ObjectMapper objectMapper;
    
    public Optional<iTunesSearchResponse> search(iTunesSearchRequest request) {
        try {
            URI uri = buildSearchUri(request);
            log.debug("iTunes API request: {}", uri);
            
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "BookLore/1.0")
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                iTunesSearchResponse searchResponse = objectMapper.readValue(response.body(), iTunesSearchResponse.class);
                log.debug("iTunes API returned {} results", searchResponse.getResultCount());
                return Optional.of(searchResponse);
            } else {
                log.warn("iTunes API request failed with status {}: {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            
        } catch (IOException | InterruptedException e) {
            log.error("Error calling iTunes API for term: {}", request.getTerm(), e);
            return Optional.empty();
        }
    }
    
    public List<String> getHighResolutionArtworkUrls(iTunesSearchResponse response) {
        return response.getResults().stream()
                .map(iTunesSearchResponse.iTunesItem::getArtworkUrl100)
                .filter(url -> url != null && !url.isEmpty())
                .map(this::convertToHighResolution)
                .toList();
    }
    
    private String convertToHighResolution(String originalUrl) {
        if (originalUrl.contains("100x100bb")) {
            return originalUrl.replace("100x100bb", "600x600bb");
        }
        if (originalUrl.contains("100x100")) {
            return originalUrl.replace("100x100", "600x600");
        }
        return originalUrl;
    }
    
    private URI buildSearchUri(iTunesSearchRequest request) {
        String term = request.getTerm();
        if (term == null || term.trim().isEmpty()) {
            throw new IllegalArgumentException("Search term cannot be null or empty");
        }
        
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(ITUNES_SEARCH_API_URL)
                .queryParam("term", URLEncoder.encode(term.trim(), StandardCharsets.UTF_8))
                .queryParam("entity", request.getEntity())
                .queryParam("country", request.getCountry())
                .queryParam("limit", request.getLimit() != null ? request.getLimit() : 25);
        
        return builder.build().toUri();
    }
}
