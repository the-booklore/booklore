package org.booklore.mobile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileBookSummary {
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
    private Float readProgress;
    private String primaryFileType;
}
