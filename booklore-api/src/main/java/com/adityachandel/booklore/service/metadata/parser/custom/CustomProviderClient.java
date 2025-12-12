package com.adityachandel.booklore.service.metadata.parser.custom;

import com.adityachandel.booklore.model.dto.settings.MetadataProviderSettings;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
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

    private final AppSettingService appSettingService;
    private final RestClient.Builder restClientBuilder;

    public CustomProviderClient(AppSettingService appSettingService, RestClient.Builder restClientBuilder) {
        this.appSettingService = appSettingService;
        this.restClientBuilder = restClientBuilder;
    }

    public CustomProviderCapabilities getCapabilities() {
        MetadataProviderSettings.CustomProvider settings = getCustomProviderSettings();
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
            log.error("Custom provider: Failed to fetch capabilities from {}, Error: {}", settings.getBaseUrl(), e.getMessage());
            return null;
        }
    }

    public List<CustomProviderBookMetadata> searchMetadata(String query, String title, String author,
                                                    String isbn13, String isbn10, String asin,
                                                    String providerId, Integer limit) {
        MetadataProviderSettings.CustomProvider settings = getCustomProviderSettings();
        if (!isConfigured(settings)) {
            return Collections.emptyList();
        }

        try {
            RestClient client = buildAuthenticatedClient(settings);

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
            log.error("Custom provider: Failed to search metadata, Error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public CustomProviderBookMetadata getMetadataById(String providerId) {
        MetadataProviderSettings.CustomProvider settings = getCustomProviderSettings();
        if (!isConfigured(settings)) {
            return null;
        }

        try {
            RestClient client = buildAuthenticatedClient(settings);

            return client.get()
                    .uri("/metadata/{providerId}", providerId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CustomProviderBookMetadata.class);
        } catch (RestClientException e) {
            log.error("Custom provider: Failed to fetch metadata for providerId={}, Error: {}", providerId, e.getMessage());
            return null;
        }
    }

    public List<CustomProviderCoverImage> searchCovers(String query, String title, String author,
                                                String isbn13, String isbn10, String providerId, String size) {
        MetadataProviderSettings.CustomProvider settings = getCustomProviderSettings();
        if (!isConfigured(settings)) {
            return Collections.emptyList();
        }

        try {
            RestClient client = buildAuthenticatedClient(settings);

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
            log.error("Custom provider: Failed to search covers, Error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<CustomProviderCoverImage> getCoversById(String providerId, String size) {
        MetadataProviderSettings.CustomProvider settings = getCustomProviderSettings();
        if (!isConfigured(settings)) {
            return Collections.emptyList();
        }

        try {
            RestClient client = buildAuthenticatedClient(settings);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/covers/{providerId}");
            if (size != null && !size.isBlank()) uriBuilder.queryParam("size", size);

            List<CustomProviderCoverImage> results = client.get()
                    .uri(uriBuilder.build(providerId).toString())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return results != null ? results : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Custom provider: Failed to fetch covers for providerId={}, Error: {}", providerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private MetadataProviderSettings.CustomProvider getCustomProviderSettings() {
        return appSettingService.getAppSettings().getMetadataProviderSettings().getCustomProvider();
    }

    private boolean isConfigured(MetadataProviderSettings.CustomProvider settings) {
        if (settings == null) {
            log.warn("Custom provider: Not configured");
            return false;
        }
        if (settings.getBaseUrl() == null || settings.getBaseUrl().isBlank()) {
            log.warn("Custom provider: Base URL not configured");
            return false;
        }
        if (settings.getBearerToken() == null || settings.getBearerToken().isBlank()) {
            log.warn("Custom provider: Bearer token not configured");
            return false;
        }
        return true;
    }

    private RestClient buildAuthenticatedClient(MetadataProviderSettings.CustomProvider settings) {
        return restClientBuilder
                .baseUrl(settings.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + settings.getBearerToken())
                .build();
    }
}
