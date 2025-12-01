package com.adityachandel.booklore.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class BookdropFinalizeResult {
    private int totalFiles;
    private int successfullyImported;
    private int failed;
    private Instant processedAt;
    @Builder.Default
    private List<BookdropFileResult> results = new ArrayList<>();
}
