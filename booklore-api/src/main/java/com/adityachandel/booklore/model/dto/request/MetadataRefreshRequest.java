package com.adityachandel.booklore.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Builder
@Data
public class MetadataRefreshRequest {
    @NotNull(message = "Refresh type cannot be null")
    private RefreshType refreshType;
    private Long libraryId;
    private Set<Long> bookIds;
    private MetadataRefreshOptions refreshOptions;

    public enum RefreshType {
        BOOKS, LIBRARY
    }
}
