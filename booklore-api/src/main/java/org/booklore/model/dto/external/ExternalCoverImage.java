package org.booklore.model.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO matching the CoverImage schema from the external metadata provider OpenAPI spec.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalCoverImage {
    private String url;
    private String size;
    private Integer width;
    private Integer height;
    private String format;
    private String providerId;
}
