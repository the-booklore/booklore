package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.NewPdfPageViewMode;
import com.adityachandel.booklore.model.enums.NewPdfPageSpread;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewPdfViewerPreferences {
    private Long bookId;
    private NewPdfPageSpread pageSpread;
    private NewPdfPageViewMode pageViewMode;
}