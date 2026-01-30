package com.adityachandel.booklore.mobile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileLibrarySummary {
    private Long id;
    private String name;
    private String icon;
    private long bookCount;
}
