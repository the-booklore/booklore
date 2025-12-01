package com.adityachandel.booklore.model.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookdropFileResult {
    private String fileName;
    private boolean success;
    private String message;
}
