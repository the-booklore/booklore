package com.adityachandel.booklore.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookViewerSetting {
    private int pageNumber;
    private String zoom;
    private String spread;
}