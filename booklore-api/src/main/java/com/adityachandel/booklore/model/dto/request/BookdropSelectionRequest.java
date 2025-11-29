package com.adityachandel.booklore.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookdropSelectionRequest {
    private boolean selectAll;
    private List<Long> excludedIds;
    private List<Long> selectedIds;
}
