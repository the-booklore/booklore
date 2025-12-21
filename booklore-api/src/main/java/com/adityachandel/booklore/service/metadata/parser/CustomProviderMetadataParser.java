package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.settings.MetadataProviderSettings;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.parser.custom.CustomProviderBookMetadata;
import com.adityachandel.booklore.service.metadata.parser.custom.CustomProviderClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class CustomProviderMetadataParser implements BookParser {

    private static final int MAX_RESULTS_PER_PROVIDER = 10;

    private final CustomProviderClient client;
    private final AppSettingService appSettingService;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest request) {
        List<MetadataProviderSettings.CustomProvider> providersToQuery = getProvidersToQuery(request);

        if (providersToQuery.isEmpty()) {
            log.debug("Custom metadata provider: No enabled providers to query");
            return Collections.emptyList();
        }

        log.debug("Custom provider: Searching metadata for title={}, author={}, isbn={} across {} providers",
                request.getTitle(), request.getAuthor(), request.getIsbn(), providersToQuery.size());

        List<BookMetadata> allResults = new ArrayList<>();

        for (MetadataProviderSettings.CustomProvider providerConfig : providersToQuery) {
            List<BookMetadata> providerResults = fetchFromProvider(providerConfig, request);
            allResults.addAll(providerResults);
        }

        log.info("Custom provider: Found {} total results from {} providers",
                allResults.size(), providersToQuery.size());
        return allResults;
    }

    private List<MetadataProviderSettings.CustomProvider> getProvidersToQuery(FetchMetadataRequest request) {
        MetadataProviderSettings providerSettings = appSettingService.getAppSettings().getMetadataProviderSettings();
        if (providerSettings == null) {
            log.warn("Metadata provider settings not configured");
            return Collections.emptyList();
        }

        List<MetadataProviderSettings.CustomProvider> allProviders = providerSettings.getCustomProviders();
        if (allProviders == null || allProviders.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> requestedIds = request.getCustomProviderIds();

        if (requestedIds != null && !requestedIds.isEmpty()) {
            return allProviders.stream()
                    .filter(p -> p.isEnabled() && requestedIds.contains(p.getId()))
                    .collect(Collectors.toList());
        }

        return allProviders.stream()
                .filter(MetadataProviderSettings.CustomProvider::isEnabled)
                .collect(Collectors.toList());
    }

    private List<BookMetadata> fetchFromProvider(MetadataProviderSettings.CustomProvider providerConfig,
                                                  FetchMetadataRequest request) {
        log.debug("Custom provider '{}': Searching metadata", providerConfig.getProviderName());

        List<CustomProviderBookMetadata> results = client.searchMetadata(
                providerConfig,
                null,
                request.getTitle(),
                request.getAuthor(),
                request.getIsbn(),
                null,
                request.getAsin(),
                null,
                MAX_RESULTS_PER_PROVIDER
        );

        if (results == null || results.isEmpty()) {
            log.debug("Custom provider '{}': No results found", providerConfig.getProviderName());
            return Collections.emptyList();
        }

        log.debug("Custom provider '{}': Found {} results",
                providerConfig.getProviderName(), results.size());

        return results.stream()
                .map(result -> mapToBookMetadata(result, providerConfig))
                .collect(Collectors.toList());
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest request) {
        List<BookMetadata> results = fetchMetadata(book, request);
        return results.isEmpty() ? null : results.get(0);
    }

    private BookMetadata mapToBookMetadata(CustomProviderBookMetadata custom,
                                           MetadataProviderSettings.CustomProvider providerConfig) {
        String seriesName = null;
        Float seriesNumber = null;
        Integer seriesTotal = null;

        if (custom.getSeries() != null && !custom.getSeries().isEmpty()) {
            CustomProviderBookMetadata.SeriesInfo series = custom.getSeries().get(0);
            seriesName = series.getName();
            seriesNumber = series.getNumber();
            seriesTotal = series.getTotal();
        }

        return BookMetadata.builder()
                .provider(MetadataProvider.CustomProvider)
                .customProviderId(custom.getProviderId())
                .customProviderName(providerConfig.getProviderName())
                .title(custom.getTitle())
                .subtitle(custom.getSubtitle())
                .authors(custom.getAuthors() != null ? new HashSet<>(custom.getAuthors()) : null)
                .publisher(custom.getPublisher())
                .publishedDate(custom.getPublishedDate())
                .description(custom.getDescription())
                .pageCount(custom.getPageCount())
                .language(custom.getLanguage())
                .isbn13(custom.getIsbn13())
                .isbn10(custom.getIsbn10())
                .asin(custom.getAsin())
                .categories(custom.getCategories() != null ? new HashSet<>(custom.getCategories()) : null)
                .moods(custom.getMoods() != null ? new HashSet<>(custom.getMoods()) : null)
                .tags(custom.getTags() != null ? new HashSet<>(custom.getTags()) : null)
                .seriesName(seriesName)
                .seriesNumber(seriesNumber)
                .seriesTotal(seriesTotal)
                .thumbnailUrl(custom.getCoverUrl() != null ? custom.getCoverUrl() : custom.getThumbnailUrl())
                .build();
    }
}
