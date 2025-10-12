package com.adityachandel.booklore.model;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetadataUpdateContext {
    private BookEntity bookEntity;
    private MetadataUpdateWrapper metadataUpdateWrapper;
    private boolean updateThumbnail;
    private boolean mergeCategories;
    private MetadataReplaceMode replaceMode;
}
