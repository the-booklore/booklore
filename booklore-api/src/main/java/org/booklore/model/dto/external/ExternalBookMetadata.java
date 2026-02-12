package org.booklore.model.dto.external;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO matching the BookMetadata schema from the external metadata provider OpenAPI spec.
 * Used for deserializing responses from custom metadata provider servers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalBookMetadata {
    private String providerId;
    private String title;
    private String subtitle;
    private List<String> authors;
    private String publisher;
    private String publishedDate;
    private String description;
    private Integer pageCount;
    private String language;
    private String isbn13;
    private String isbn10;
    private String asin;
    private List<String> categories;
    private List<String> moods;
    private List<String> tags;
    private List<ExternalSeriesInfo> series;
    private Double rating;
    private Integer reviewCount;
    private String coverUrl;
    private Double matchScore;
}
