package com.adityachandel.booklore.model.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class BookdropPatternExtractRequest {
    private String pattern;
    private Boolean selectAll;
    private List<Long> excludedIds;
    private List<Long> selectedIds;
    private Boolean preview;
}
