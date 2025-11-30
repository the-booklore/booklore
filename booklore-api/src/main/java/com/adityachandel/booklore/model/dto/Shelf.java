package com.adityachandel.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Shelf {
    private Long id;
    private String name;
    private String icon;
    private Sort sort;
    private Long userId;
}
