package com.adityachandel.booklore.task;

import com.adityachandel.booklore.task.tasks.options.LibraryRescanOptions;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RescanLibraryContext {
    private Long libraryId;
    private LibraryRescanOptions options;
}
