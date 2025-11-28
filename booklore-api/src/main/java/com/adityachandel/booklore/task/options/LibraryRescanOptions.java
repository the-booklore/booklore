package com.adityachandel.booklore.task.options;

import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LibraryRescanOptions {

    private boolean updateMetadataFromFiles;
    private MetadataReplaceMode metadataReplaceMode;
}