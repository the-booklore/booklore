package com.adityachandel.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RuleField {
    @JsonProperty("library")
    LIBRARY,
    @JsonProperty("title")
    TITLE,
    @JsonProperty("subtitle")
    SUBTITLE,
    @JsonProperty("authors")
    AUTHORS,
    @JsonProperty("categories")
    CATEGORIES,
    @JsonProperty("publisher")
    PUBLISHER,
    @JsonProperty("publishedDate")
    PUBLISHED_DATE,
    @JsonProperty("seriesName")
    SERIES_NAME,
    @JsonProperty("seriesNumber")
    SERIES_NUMBER,
    @JsonProperty("seriesTotal")
    SERIES_TOTAL,
    @JsonProperty("pageCount")
    PAGE_COUNT,
    @JsonProperty("language")
    LANGUAGE,
    @JsonProperty("amazonRating")
    AMAZON_RATING,
    @JsonProperty("amazonReviewCount")
    AMAZON_REVIEW_COUNT,
    @JsonProperty("goodreadsRating")
    GOODREADS_RATING,
    @JsonProperty("goodreadsReviewCount")
    GOODREADS_REVIEW_COUNT,
    @JsonProperty("hardcoverRating")
    HARDCOVER_RATING,
    @JsonProperty("hardcoverReviewCount")
    HARDCOVER_REVIEW_COUNT,
    @JsonProperty("personalRating")
    PERSONAL_RATING,
    @JsonProperty("fileType")
    FILE_TYPE,
    @JsonProperty("fileSize")
    FILE_SIZE,
    @JsonProperty("readStatus")
    READ_STATUS,
    @JsonProperty("dateFinished")
    DATE_FINISHED,
    @JsonProperty("lastReadTime")
    LAST_READ_TIME,
    @JsonProperty("metadataScore")
    METADATA_SCORE,
    @JsonProperty("moods")
    MOODS,
    @JsonProperty("tags")
    TAGS
}

