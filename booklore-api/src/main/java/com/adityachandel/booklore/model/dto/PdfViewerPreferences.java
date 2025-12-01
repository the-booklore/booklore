package com.adityachandel.booklore.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PdfViewerPreferences {
    private Long bookId;
    private String zoom;
    private String spread;
}