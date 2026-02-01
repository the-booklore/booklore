package com.adityachandel.booklore.mobile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileBookDetail {
    private Long id;
    private String title;
    private List<String> authors;
    private String thumbnailUrl;
    private String readStatus;
    private Integer personalRating;
    private String seriesName;
    private Float seriesNumber;
    private Long libraryId;
    private Instant addedOn;
    private Instant lastReadTime;

    private String subtitle;
    private String description;
    private Set<String> categories;
    private String publisher;
    private LocalDate publishedDate;
    private Integer pageCount;
    private String isbn13;
    private String language;
    private Double goodreadsRating;
    private Integer goodreadsReviewCount;
    private String libraryName;
    private List<MobileShelfSummary> shelves;
    private Float readProgress;
    private String primaryFileType;
    private List<String> fileTypes;
    private List<MobileBookFile> files;

    private EpubProgress epubProgress;
    private PdfProgress pdfProgress;
    private CbxProgress cbxProgress;
    private AudiobookProgress audiobookProgress;

    @Data
    @Builder
    public static class EpubProgress {
        private String cfi;
        private String href;
        private Float percentage;
        private Instant updatedAt;
    }

    @Data
    @Builder
    public static class PdfProgress {
        private Integer page;
        private Float percentage;
        private Instant updatedAt;
    }

    @Data
    @Builder
    public static class CbxProgress {
        private Integer page;
        private Float percentage;
        private Instant updatedAt;
    }

    @Data
    @Builder
    public static class AudiobookProgress {
        private Long positionMs;
        private Integer trackIndex;
        private Float percentage;
        private Instant updatedAt;
    }
}
