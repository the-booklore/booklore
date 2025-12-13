package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.dto.BookMetadata;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class BookdropBulkEditRequest {
    private BookMetadata fields;
    private Set<String> enabledFields;
    private boolean mergeArrays;
    private boolean selectAll;
    private List<Long> excludedIds;
    private List<Long> selectedIds;
}
