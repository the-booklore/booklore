package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.MetadataProvider;
import lombok.Data;

import java.util.Set;

@Data
public class BooksMetadataRefreshRequest {
    private Set<Long> bookIds;
    private MetadataProvider metadataProvider;
    private boolean replaceCover;
}
