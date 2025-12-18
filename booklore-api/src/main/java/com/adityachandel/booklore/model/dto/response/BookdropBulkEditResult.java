package com.adityachandel.booklore.model.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookdropBulkEditResult {
    private int totalFiles;
    private int successfullyUpdated;
    private int failed;
}
