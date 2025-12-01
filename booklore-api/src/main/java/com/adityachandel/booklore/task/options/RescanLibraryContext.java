package com.adityachandel.booklore.task.options;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RescanLibraryContext {
    private Long libraryId;
    private LibraryRescanOptions options;
}
