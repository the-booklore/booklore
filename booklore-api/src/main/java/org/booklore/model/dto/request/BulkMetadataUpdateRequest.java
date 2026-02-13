package org.booklore.model.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class BulkMetadataUpdateRequest {
    private Set<Long> bookIds;

    private Set<String> authors;
    private Boolean clearAuthors;

    private String publisher;
    private Boolean clearPublisher;

    private String language;
    private Boolean clearLanguage;

    private String seriesName;
    private Boolean clearSeriesName;

    private Integer seriesTotal;
    private Boolean clearSeriesTotal;

    private LocalDate publishedDate;
    private Boolean clearPublishedDate;

    private Set<String> genres;
    private Boolean clearGenres;

    private Set<String> moods;
    private Boolean clearMoods;

    private Set<String> tags;
    private Boolean clearTags;

    private Boolean mergeCategories;
    private Boolean mergeMoods;
    private Boolean mergeTags;

    private Integer ageRating;
    private Boolean clearAgeRating;

    private String contentRating;
    private Boolean clearContentRating;
}
