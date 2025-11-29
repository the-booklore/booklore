package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CbxViewerPreferences {
    private Long bookId;
    private CbxPageSpread pageSpread;
    private CbxPageViewMode pageViewMode;
    private CbxPageFitMode fitMode;
    private CbxPageScrollMode scrollMode;
    private CbxBackgroundColor backgroundColor;
}