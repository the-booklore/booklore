package com.adityachandel.booklore.model;

import com.adityachandel.booklore.model.dto.BookMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataUpdateWrapper {
    private BookMetadata metadata;
    @Builder.Default
    private MetadataClearFlags clearFlags = new MetadataClearFlags();
}
