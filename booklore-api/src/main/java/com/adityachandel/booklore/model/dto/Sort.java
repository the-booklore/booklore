package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.SortDirection;
import lombok.Data;

@Data
public class Sort {
    private String field;
    private SortDirection direction;
}
