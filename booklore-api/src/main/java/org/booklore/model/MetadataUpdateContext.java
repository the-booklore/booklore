package org.booklore.model;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.MetadataReplaceMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetadataUpdateContext {
    private BookEntity bookEntity;
    private MetadataUpdateWrapper metadataUpdateWrapper;
    private boolean updateThumbnail;
    private boolean mergeCategories;
    private boolean mergeMoods;
    private boolean mergeTags;
    private MetadataReplaceMode replaceMode;
}
