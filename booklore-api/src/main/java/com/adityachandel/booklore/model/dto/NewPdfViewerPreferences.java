package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.*;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NewPdfViewerPreferences {
    private Long bookId;
    private NewPdfPageSpread pageSpread;
    private NewPdfPageViewMode pageViewMode;
    private NewPdfBackgroundColor backgroundColor;
    private NewPdfPageFitMode fitMode;
    private NewPdfPageScrollMode scrollMode;
}