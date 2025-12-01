package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.MetadataProvider;
import lombok.Data;

@Data
public class LibraryMetadataRefreshRequest {
    private Long libraryId;
    private MetadataProvider metadataProvider;
    private boolean replaceCover;
}
