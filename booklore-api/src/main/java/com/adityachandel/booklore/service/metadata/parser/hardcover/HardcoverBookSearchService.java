package com.adityachandel.booklore.service.metadata.parser.hardcover;

import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class HardcoverBookSearchService {

    public static final int DEFAULT_PER_PAGE = 10;
    private static final long INITIAL_DELAY_MS = 1200;
    private static final long MAX_DELAY_MS = 15000;

    private final RestClient restClient;
    private final AppSettingService appSettingService;
    private final AtomicLong requestDelayMs = new AtomicLong(INITIAL_DELAY_MS);
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);

    @Autowired
    public HardcoverBookSearchService(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.hardcover.app/v1/graphql")
                .build();
    }

    public List<GraphQLResponse.Hit> searchBooks(String query) {
        return searchBooks(query, DEFAULT_PER_PAGE);
    }

    public List<GraphQLResponse.Hit> searchBooks(String query, int perPage) {
        String apiToken = getApiToken();
        if (apiToken == null) {
            return Collections.emptyList();
        }

        int sanitizedPerPage = Math.min(Math.max(perPage, 1), 100);
        
        GraphQLRequest body = new GraphQLRequest();
        body.setQuery("query BookSearch($q: String!, $limit: Int!) { search(query: $q, query_type: \"Book\", per_page: $limit, page: 1) { results } }");
        body.setVariables(java.util.Map.of("q", query, "limit", sanitizedPerPage));

        GraphQLResponse response = executeRequest(body, GraphQLResponse.class, apiToken);
        if (response == null || response.getData() == null ||
                response.getData().getSearch() == null ||
                response.getData().getSearch().getResults() == null) {
            return Collections.emptyList();
        }

        List<GraphQLResponse.Hit> hits = response.getData().getSearch().getResults().getHits();
        return hits != null ? hits : Collections.emptyList();
    }

    public HardcoverBookDetails fetchBookDetails(int bookId) {
        String apiToken = getApiToken();
        if (apiToken == null) {
            return null;
        }

        GraphQLRequest body = new GraphQLRequest();
        body.setQuery("query BookDetails($id: Int!) { books_by_pk(id: $id) { id title cached_tags } }");
        body.setVariables(java.util.Map.of("id", bookId));

        HardcoverBookDetailsResponse response = executeRequest(body, HardcoverBookDetailsResponse.class, apiToken);
        if (response == null || response.getData() == null) {
            return null;
        }
        return response.getData().getBooksByPk();
    }

    private <T> T executeRequest(GraphQLRequest body, Class<T> responseType, String apiToken) {
        enforceRateLimit();

        try {
            T response = restClient.post()
                    .uri("")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .body(body)
                    .retrieve()
                    .body(responseType);

            successCount.incrementAndGet();
            if (successCount.get() % 5 == 0) {
                reduceDelay();
            }
            return response;

        } catch (RestClientResponseException e) {
            successCount.set(0);
            if (e.getStatusCode().value() == 429 || e.getResponseBodyAsString().contains("Throttled")) {
                increaseDelay();
                log.warn("Hardcover API throttled, increased delay to {}ms", requestDelayMs.get());
            } else {
                log.error("Hardcover API error: {}", e.getMessage());
            }
            return null;
        } catch (Exception e) {
            log.error("Hardcover API request failed: {}", e.getMessage());
            return null;
        } finally {
            lastRequestTime.set(System.currentTimeMillis());
        }
    }

    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long last = lastRequestTime.get();
        long delay = requestDelayMs.get();
        long elapsed = now - last;

        if (elapsed < delay) {
            try {
                Thread.sleep(delay - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void increaseDelay() {
        requestDelayMs.updateAndGet(current -> Math.min(current * 2, MAX_DELAY_MS));
    }

    private void reduceDelay() {
        requestDelayMs.updateAndGet(current -> Math.max(current - 100, INITIAL_DELAY_MS));
    }

    private String getApiToken() {
        String apiToken = appSettingService.getAppSettings().getMetadataProviderSettings().getHardcover().getApiKey();
        if (apiToken == null || apiToken.isEmpty()) {
            log.warn("Hardcover API token not set");
            return null;
        }
        return apiToken;
    }
}
