package com.adityachandel.booklore.model.dto.response.comicvineapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Comic {

    private int id;

    @JsonProperty("api_detail_url")
    private String apiDetailUrl;

    @JsonProperty("cover_date")
    private String coverDate;

    private String description;

    private String name;

    @JsonProperty("issue_number")
    private String issueNumber;

    private Image image;

    private Volume volume;

    @JsonProperty("resource_type")
    private String resourceType;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Image {
        @JsonProperty("icon_url")
        private String iconUrl;

        @JsonProperty("medium_url")
        private String mediumUrl;

        @JsonProperty("screen_url")
        private String screenUrl;

        @JsonProperty("screen_large_url")
        private String screenLargeUrl;

        @JsonProperty("small_url")
        private String smallUrl;

        @JsonProperty("super_url")
        private String superUrl;

        @JsonProperty("thumb_url")
        private String thumbUrl;

        @JsonProperty("tiny_url")
        private String tinyUrl;

        @JsonProperty("original_url")
        private String originalUrl;

        @JsonProperty("image_tags")
        private String imageTags;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Volume {
        private int id;
        private String name;

        @JsonProperty("api_detail_url")
        private String apiDetailUrl;

        @JsonProperty("site_detail_url")
        private String siteDetailUrl;
    }
}