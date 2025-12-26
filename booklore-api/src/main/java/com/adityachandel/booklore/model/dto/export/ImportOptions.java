package com.adityachandel.booklore.model.dto.export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Options for importing configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportOptions {
    /**
     * Import regular shelves
     */
    @Builder.Default
    private boolean importShelves = true;

    /**
     * Import magic shelves
     */
    @Builder.Default
    private boolean importMagicShelves = true;

    /**
     * Import user settings
     */
    @Builder.Default
    private boolean importSettings = true;

    /**
     * Skip items that already exist (default behavior)
     */
    @Builder.Default
    private boolean skipExisting = true;

    /**
     * Overwrite existing items (conflicts with skipExisting)
     * If both are true, skipExisting takes precedence
     */
    @Builder.Default
    private boolean overwrite = false;

    /**
     * Don't import book mappings for shelves (just shelf structure)
     * Book IDs may not match across different libraries
     */
    @Builder.Default
    private boolean skipBookMappings = true;
}
