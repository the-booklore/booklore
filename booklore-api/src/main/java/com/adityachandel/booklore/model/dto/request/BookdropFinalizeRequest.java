package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.dto.BookMetadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookdropFinalizeRequest {
    private Boolean selectAll;
    private List<Long> excludedIds;
    private List<BookdropFinalizeFile> files;
    private Long defaultLibraryId;
    private Long defaultPathId;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BookdropFinalizeFile {
        private Long fileId;
        private Long libraryId;
        private Long pathId;
        private BookMetadata metadata;
    }
}