package com.adityachandel.booklore.mobile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileMagicShelfSummary {
    private Long id;
    private String name;
    private String icon;
    private String iconType;
    private boolean publicShelf;
}
