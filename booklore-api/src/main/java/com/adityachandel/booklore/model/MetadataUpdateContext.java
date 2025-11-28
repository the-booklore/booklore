package com.adityachandel.booklore.model;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataUpdateContext {
    private BookEntity bookEntity;
    private MetadataUpdateWrapper metadataUpdateWrapper;
    private boolean updateThumbnail;
    private boolean mergeCategories;
    private boolean mergeMoods;
    private boolean mergeTags;
    private MetadataReplaceMode replaceMode;
}
