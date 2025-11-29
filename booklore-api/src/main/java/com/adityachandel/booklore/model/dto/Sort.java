package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.SortDirection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Sort {
    private String field;
    private SortDirection direction;
}
