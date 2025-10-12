package com.adityachandel.booklore.task.tasks.options;

import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LibraryRescanOptions {

    private boolean updateMetadataFromFiles;
    private MetadataReplaceMode metadataReplaceMode;
}