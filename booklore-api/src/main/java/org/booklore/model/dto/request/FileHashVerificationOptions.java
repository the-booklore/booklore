package org.booklore.model.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileHashVerificationOptions {
    /**
     * If true, only reports mismatches without updating the database
     */
    @Builder.Default
    private boolean dryRun = false;

    /**
     * If true, overwrites the initial_hash even if it already exists
     * If false, only moves current_hash to initial_hash if initial_hash is null
     */
    @Builder.Default
    private boolean overwriteInitialHash = false;
}
