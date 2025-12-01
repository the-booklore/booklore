package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.MetadataProvider;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class FetchMetadataRequest {
    private Long bookId;
    private List<MetadataProvider> providers;
    private String isbn;
    private String title;
    private String author;
    private String asin;
}
