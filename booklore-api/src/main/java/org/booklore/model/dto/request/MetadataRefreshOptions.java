package org.booklore.model.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.model.enums.MetadataReplaceMode;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetadataRefreshOptions {
    private Long libraryId;
    private Boolean refreshCovers;
    private Boolean mergeCategories;
    private Boolean reviewBeforeApply;
    /**
     * Controls how fetched metadata replaces existing metadata.
     * REPLACE_ALL: Replace all fields with fetched values (even if existing values are present)
     * REPLACE_MISSING: Only fill in fields that are currently empty/null
     * Default is REPLACE_MISSING to preserve existing metadata unless user explicitly wants to overwrite.
     */
    @Builder.Default
    private MetadataReplaceMode replaceMode = MetadataReplaceMode.REPLACE_MISSING;
    @NotNull(message = "Field options cannot be null")
    @Builder.Default
    private FieldOptions fieldOptions = new FieldOptions();
    @NotNull(message = "Enabled fields cannot be null")
    @Builder.Default
    private EnabledFields enabledFields = new EnabledFields();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FieldOptions {
        private FieldProvider title;
        private FieldProvider subtitle;
        private FieldProvider description;
        private FieldProvider authors;
        private FieldProvider publisher;
        private FieldProvider publishedDate;
        private FieldProvider seriesName;
        private FieldProvider seriesNumber;
        private FieldProvider seriesTotal;
        private FieldProvider isbn13;
        private FieldProvider isbn10;
        private FieldProvider language;
        private FieldProvider categories;
        private FieldProvider cover;
        private FieldProvider pageCount;
        private FieldProvider asin;
        private FieldProvider goodreadsId;
        private FieldProvider comicvineId;
        private FieldProvider hardcoverId;
        private FieldProvider googleId;
        private FieldProvider lubimyczytacId;
        private FieldProvider amazonRating;
        private FieldProvider amazonReviewCount;
        private FieldProvider goodreadsRating;
        private FieldProvider goodreadsReviewCount;
        private FieldProvider hardcoverRating;
        private FieldProvider hardcoverReviewCount;
        private FieldProvider lubimyczytacRating;
        private FieldProvider ranobedbId;
        private FieldProvider ranobedbRating;
        private FieldProvider moods;
        private FieldProvider tags;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FieldProvider {
        private MetadataProvider p1;
        private MetadataProvider p2;
        private MetadataProvider p3;
        private MetadataProvider p4;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @Builder
    public static class EnabledFields {
        // All fields default to true so metadata fetcher updates fields by default
        // unless explicitly disabled by the user.
        // @JsonSetter(nulls = Nulls.SKIP) ensures that null values in persisted JSON
        // (e.g. from older versions missing newer fields) are ignored, preserving defaults.
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean title = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean subtitle = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean description = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean authors = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean publisher = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean publishedDate = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean seriesName = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean seriesNumber = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean seriesTotal = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean isbn13 = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean isbn10 = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean language = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean categories = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean cover = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean pageCount = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean asin = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean goodreadsId = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean comicvineId = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean hardcoverId = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean googleId = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean lubimyczytacId = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean amazonRating = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean amazonReviewCount = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean goodreadsRating = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean goodreadsReviewCount = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean hardcoverRating = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean hardcoverReviewCount = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean lubimyczytacRating = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean ranobedbId = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean ranobedbRating = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean moods = true;
        @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
        private Boolean tags = true;

        /**
         * Default constructor that initializes all fields to true.
         * This ensures that by default, all metadata fields are enabled for fetching/updating.
         */
        public EnabledFields() {
            this.title = true;
            this.subtitle = true;
            this.description = true;
            this.authors = true;
            this.publisher = true;
            this.publishedDate = true;
            this.seriesName = true;
            this.seriesNumber = true;
            this.seriesTotal = true;
            this.isbn13 = true;
            this.isbn10 = true;
            this.language = true;
            this.categories = true;
            this.cover = true;
            this.pageCount = true;
            this.asin = true;
            this.goodreadsId = true;
            this.comicvineId = true;
            this.hardcoverId = true;
            this.googleId = true;
            this.lubimyczytacId = true;
            this.amazonRating = true;
            this.amazonReviewCount = true;
            this.goodreadsRating = true;
            this.goodreadsReviewCount = true;
            this.hardcoverRating = true;
            this.hardcoverReviewCount = true;
            this.lubimyczytacRating = true;
            this.ranobedbId = true;
            this.ranobedbRating = true;
            this.moods = true;
            this.tags = true;
        }
    }
}
