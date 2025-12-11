package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.settings.MetadataProviderSettings;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.parser.custom.CustomBookMetadata;
import com.adityachandel.booklore.service.metadata.parser.custom.CustomProviderClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class CustomMetadataParser implements BookParser {

    private final CustomProviderClient client;
    private final AppSettingService appSettingService;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest request) {
        if (!isEnabled()) {
            log.debug("Custom metadata provider is disabled");
            return Collections.emptyList();
        }

        log.debug("Custom provider: Searching metadata for title={}, author={}, isbn={}",
                request.getTitle(), request.getAuthor(), request.getIsbn());

        List<CustomBookMetadata> results = client.searchMetadata(
                null,
                request.getTitle(),
                request.getAuthor(),
                request.getIsbn(),
                null,
                request.getAsin(),
                null,
                10
        );

        if (results == null || results.isEmpty()) {
            log.debug("Custom provider: No results found");
            return Collections.emptyList();
        }

        log.info("Custom provider: Found {} results", results.size());
        return results.stream()
                .map(this::mapToBookMetadata)
                .collect(Collectors.toList());
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest request) {
        List<BookMetadata> results = fetchMetadata(book, request);
        return results.isEmpty() ? null : results.get(0);
    }

    private BookMetadata mapToBookMetadata(CustomBookMetadata custom) {
        String seriesName = null;
        Float seriesNumber = null;
        Integer seriesTotal = null;

        if (custom.getSeries() != null && !custom.getSeries().isEmpty()) {
            CustomBookMetadata.SeriesInfo series = custom.getSeries().get(0);
            seriesName = series.getName();
            seriesNumber = series.getNumber();
            seriesTotal = series.getTotal();
        }

        return BookMetadata.builder()
                .provider(MetadataProvider.Custom)
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
                .rating(custom.getRating())
                .thumbnailUrl(custom.getCoverUrl() != null ? custom.getCoverUrl() : custom.getThumbnailUrl())
                .build();
    }

    private boolean isEnabled() {
        MetadataProviderSettings.Custom settings = appSettingService.getAppSettings()
                .getMetadataProviderSettings()
                .getCustom();
        return settings != null
                && settings.isEnabled()
                && settings.getBaseUrl() != null
                && !settings.getBaseUrl().isBlank();
    }
}
