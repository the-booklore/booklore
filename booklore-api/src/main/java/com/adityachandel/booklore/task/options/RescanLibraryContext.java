package com.adityachandel.booklore.task.options;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RescanLibraryContext {
    private Long libraryId;
    private LibraryRescanOptions options;
}
