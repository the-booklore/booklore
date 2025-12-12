package com.adityachandel.booklore.service.metadata.parser.custom;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CustomProviderBookMetadata {
    private String providerId;
    private String title;
    private String subtitle;
    private List<String> authors;
    private String publisher;
    private LocalDate publishedDate;
    private String description;
    private Integer pageCount;
    private String language;
    private String isbn13;
    private String isbn10;
    private String asin;
    private List<String> categories;
    private List<String> moods;
    private List<String> tags;
    private List<SeriesInfo> series;
    private Double rating;
    private Integer reviewCount;
    private String coverUrl;
    private String thumbnailUrl;
    private Double matchScore;

    @Data
    public static class SeriesInfo {
        private String name;
        private Float number;
        private Integer total;
    }
}
