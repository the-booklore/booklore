package org.booklore.task.options;

import org.booklore.model.enums.MetadataReplaceMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LibraryRescanOptions {

    private boolean updateMetadataFromFiles;
    private MetadataReplaceMode metadataReplaceMode;
}