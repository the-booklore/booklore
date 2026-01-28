package com.adityachandel.booklore.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookMarkRequest {
    @NotNull
    private Long bookId;

    // For EPUB bookmarks
    private String cfi;

    // For audiobook bookmarks
    private Long positionMs;
    private Integer trackIndex;

    private String title;

    /**
     * Check if this is an audiobook bookmark (has positionMs) vs EPUB bookmark (has cfi)
     */
    public boolean isAudiobookBookmark() {
        return positionMs != null;
    }
}
