package com.adityachandel.booklore.model.dto.response;

import com.adityachandel.booklore.model.dto.BookMetadata;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BookdropPatternExtractResult {
    private int totalFiles;
    private int successfullyExtracted;
    private int failed;
    private List<FileExtractionResult> results;

    @Data
    @Builder
    public static class FileExtractionResult {
        private Long fileId;
        private String fileName;
        private boolean success;
        private BookMetadata extractedMetadata;
        private String errorMessage;
    }
}
