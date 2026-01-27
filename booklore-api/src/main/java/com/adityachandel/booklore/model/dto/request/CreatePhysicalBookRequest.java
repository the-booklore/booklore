package com.adityachandel.booklore.model.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreatePhysicalBookRequest {
    @NotNull(message = "Library ID must not be null.")
    private Long libraryId;

    private String isbn;
    private String title;
    private List<String> authors;
    private String description;
    private String publisher;
    private String publishedDate;
    private String language;
    private Integer pageCount;
    private List<String> categories;
}
