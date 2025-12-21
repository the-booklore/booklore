package com.adityachandel.booklore.service.metadata.parser.custom;

import com.adityachandel.booklore.model.dto.settings.MetadataProviderSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class CustomProviderClient {

    private final RestClient.Builder restClientBuilder;

    public CustomProviderClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public CustomProviderCapabilities getCapabilities(MetadataProviderSettings.CustomProvider settings) {
        if (settings == null || settings.getBaseUrl() == null || settings.getBaseUrl().isBlank()) {
            log.warn("Custom provider: Base URL not configured");
            return null;
        }

        try {
            RestClient client = restClientBuilder
                    .baseUrl(settings.getBaseUrl())
                    .build();

            return client.get()
                    .uri("/capabilities")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CustomProviderCapabilities.class);
        } catch (RestClientException e) {
            log.error("Custom provider '{}': Failed to fetch capabilities from {}, Error: {}",
                    settings.getProviderName(), settings.getBaseUrl(), e.getMessage());
            return null;
        }
    }

    public List<CustomProviderBookMetadata> searchMetadata(MetadataProviderSettings.CustomProvider settings,
                                                           String query, String title, String author,
                                                           String isbn13, String isbn10, String asin,
                                                           String providerId, Integer limit) {
        if (!isConfigured(settings)) {
            return Collections.emptyList();
        }

        try {
            RestClient client = buildClient(settings);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/metadata/search");
            if (query != null && !query.isBlank()) uriBuilder.queryParam("query", query);
            if (title != null && !title.isBlank()) uriBuilder.queryParam("title", title);
            if (author != null && !author.isBlank()) uriBuilder.queryParam("author", author);
            if (isbn13 != null && !isbn13.isBlank()) uriBuilder.queryParam("isbn13", isbn13);
            if (isbn10 != null && !isbn10.isBlank()) uriBuilder.queryParam("isbn10", isbn10);
            if (asin != null && !asin.isBlank()) uriBuilder.queryParam("asin", asin);
            if (providerId != null && !providerId.isBlank()) uriBuilder.queryParam("providerId", providerId);
            if (limit != null) uriBuilder.queryParam("limit", limit);

            List<CustomProviderBookMetadata> results = client.get()
                    .uri(uriBuilder.build().toUriString())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return results != null ? results : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Custom provider '{}': Failed to search metadata, Error: {}",
                    settings.getProviderName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    public CustomProviderBookMetadata getMetadataById(MetadataProviderSettings.CustomProvider settings, String providerId) {
        if (!isConfigured(settings)) {
            return null;
        }

        try {
            RestClient client = buildClient(settings);

            return client.get()
                    .uri("/metadata/{providerId}", providerId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CustomProviderBookMetadata.class);
        } catch (RestClientException e) {
            log.error("Custom provider '{}': Failed to fetch metadata for providerId={}, Error: {}",
                    settings.getProviderName(), providerId, e.getMessage());
            return null;
        }
    }

    public List<CustomProviderCoverImage> searchCovers(MetadataProviderSettings.CustomProvider settings,
                                                       String query, String title, String author,
                                                       String isbn13, String isbn10, String providerId, String size) {
        if (!isConfigured(settings)) {
            return Collections.emptyList();
        }

        try {
            RestClient client = buildClient(settings);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/covers/search");
            if (query != null && !query.isBlank()) uriBuilder.queryParam("query", query);
            if (title != null && !title.isBlank()) uriBuilder.queryParam("title", title);
            if (author != null && !author.isBlank()) uriBuilder.queryParam("author", author);
            if (isbn13 != null && !isbn13.isBlank()) uriBuilder.queryParam("isbn13", isbn13);
            if (isbn10 != null && !isbn10.isBlank()) uriBuilder.queryParam("isbn10", isbn10);
            if (providerId != null && !providerId.isBlank()) uriBuilder.queryParam("providerId", providerId);
            if (size != null && !size.isBlank()) uriBuilder.queryParam("size", size);

            List<CustomProviderCoverImage> results = client.get()
                    .uri(uriBuilder.build().toUriString())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return results != null ? results : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Custom provider '{}': Failed to search covers, Error: {}",
                    settings.getProviderName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<CustomProviderCoverImage> getCoversById(MetadataProviderSettings.CustomProvider settings,
                                                        String providerId, String size) {
        if (!isConfigured(settings)) {
            return Collections.emptyList();
        }

        try {
            RestClient client = buildClient(settings);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/covers/{providerId}");
            if (size != null && !size.isBlank()) uriBuilder.queryParam("size", size);

            List<CustomProviderCoverImage> results = client.get()
                    .uri(uriBuilder.build(providerId).toString())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return results != null ? results : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Custom provider '{}': Failed to fetch covers for providerId={}, Error: {}",
                    settings.getProviderName(), providerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean isConfigured(MetadataProviderSettings.CustomProvider settings) {
        if (settings == null) {
            log.warn("Custom provider: Not configured");
            return false;
        }
        if (settings.getBaseUrl() == null || settings.getBaseUrl().isBlank()) {
            log.warn("Custom provider '{}': Base URL not configured", settings.getProviderName());
            return false;
        }
        return true;
    }

    private RestClient buildClient(MetadataProviderSettings.CustomProvider settings) {
        RestClient.Builder builder = restClientBuilder.baseUrl(settings.getBaseUrl());
        if (settings.getBearerToken() != null && !settings.getBearerToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + settings.getBearerToken());
        }
        return builder.build();
    }
}
