package com.adityachandel.booklore.mobile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileBookFile {
    private Long id;
    private String bookType;
    private Long fileSizeKb;
    private String fileName;
    private boolean isPrimary;
}
