package org.booklore.service.metadata.parser.custom;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.external.ExternalBookMetadata;
import org.booklore.model.dto.external.ExternalCoverImage;
import org.booklore.model.dto.external.ExternalProviderCapabilities;
import org.booklore.model.dto.settings.CustomMetadataProviderConfig;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * HTTP client that communicates with a single external metadata provider server
 * conforming to the Book Metadata Provider API spec.
 * <p>
 * Each instance is bound to one {@link CustomMetadataProviderConfig} and handles
 * authentication, request building, and error handling for that provider.
 */
@Slf4j
public class CustomProviderClient {

    private final RestClient restClient;
    private final CustomMetadataProviderConfig config;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    public CustomProviderClient(RestClient.Builder restClientBuilder, CustomMetadataProviderConfig config) {
        this.config = config;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        RestClient.Builder builder = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(config.getBaseUrl());

        if (config.getBearerToken() != null && !config.getBearerToken().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + config.getBearerToken());
        }

        this.restClient = builder.build();
    }

    /**
     * Fetches provider capabilities from GET /capabilities.
     * This endpoint does not require authentication per the spec.
     */
    public ExternalProviderCapabilities fetchCapabilities() {
        try {
            return restClient.get()
                    .uri("/capabilities")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        log.error("Error fetching capabilities from provider '{}': HTTP {}", config.getName(), response.getStatusCode());
                    })
                    .body(ExternalProviderCapabilities.class);
        } catch (Exception e) {
            log.error("Failed to fetch capabilities from provider '{}' at {}", config.getName(), config.getBaseUrl(), e);
            return null;
        }
    }

    /**
     * Searches for book metadata via GET /metadata/search.
     */
    public List<ExternalBookMetadata> searchMetadata(String query, String title, String author,
                                                      String isbn13, String isbn10, String asin,
                                                      String providerId, Integer limit) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/metadata/search");
                        if (query != null && !query.isBlank()) uriBuilder.queryParam("query", query);
                        if (title != null && !title.isBlank()) uriBuilder.queryParam("title", title);
                        if (author != null && !author.isBlank()) uriBuilder.queryParam("author", author);
                        if (isbn13 != null && !isbn13.isBlank()) uriBuilder.queryParam("isbn13", isbn13);
                        if (isbn10 != null && !isbn10.isBlank()) uriBuilder.queryParam("isbn10", isbn10);
                        if (asin != null && !asin.isBlank()) uriBuilder.queryParam("asin", asin);
                        if (providerId != null && !providerId.isBlank()) uriBuilder.queryParam("providerId", providerId);
                        if (limit != null) uriBuilder.queryParam("limit", limit);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        log.error("Error searching metadata from provider '{}': HTTP {}", config.getName(), response.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to search metadata from provider '{}': {}", config.getName(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches detailed metadata for a specific book via GET /metadata/{providerId}.
     */
    public ExternalBookMetadata getMetadataById(String providerId) {
        try {
            return restClient.get()
                    .uri("/metadata/{providerId}", providerId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        log.error("Error fetching metadata by ID from provider '{}': HTTP {}", config.getName(), response.getStatusCode());
                    })
                    .body(ExternalBookMetadata.class);
        } catch (Exception e) {
            log.error("Failed to fetch metadata by ID '{}' from provider '{}': {}", providerId, config.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Searches for cover images via GET /covers/search.
     */
    public List<ExternalCoverImage> searchCovers(String query, String title, String author,
                                                  String isbn13, String isbn10, String providerId,
                                                  String size) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/covers/search");
                        if (query != null && !query.isBlank()) uriBuilder.queryParam("query", query);
                        if (title != null && !title.isBlank()) uriBuilder.queryParam("title", title);
                        if (author != null && !author.isBlank()) uriBuilder.queryParam("author", author);
                        if (isbn13 != null && !isbn13.isBlank()) uriBuilder.queryParam("isbn13", isbn13);
                        if (isbn10 != null && !isbn10.isBlank()) uriBuilder.queryParam("isbn10", isbn10);
                        if (providerId != null && !providerId.isBlank()) uriBuilder.queryParam("providerId", providerId);
                        if (size != null && !size.isBlank()) uriBuilder.queryParam("size", size);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        log.error("Error searching covers from provider '{}': HTTP {}", config.getName(), response.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to search covers from provider '{}': {}", config.getName(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches cover images for a specific book via GET /covers/{providerId}.
     */
    public List<ExternalCoverImage> getCoversById(String providerId, String size) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/covers/{providerId}");
                        if (size != null && !size.isBlank()) uriBuilder.queryParam("size", size);
                        return uriBuilder.build(providerId);
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        log.error("Error fetching covers by ID from provider '{}': HTTP {}", config.getName(), response.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch covers by ID '{}' from provider '{}': {}", providerId, config.getName(), e.getMessage());
            return List.of();
        }
    }

    public CustomMetadataProviderConfig getConfig() {
        return config;
    }
}
