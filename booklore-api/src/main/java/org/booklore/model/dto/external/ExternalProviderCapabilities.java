package org.booklore.model.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO matching the ProviderCapabilities schema from the external metadata provider OpenAPI spec.
 * Returned by the /capabilities endpoint of a custom provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalProviderCapabilities {
    private String providerId;
    private String providerName;
    private String version;
    private Capabilities capabilities;
    private RateLimit rateLimit;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Capabilities {
        private Boolean supportsMetadata;
        private Boolean supportsCovers;
        private Boolean supportsIsbnSearch;
        private Boolean supportsTitleAuthorSearch;
        private Boolean supportsRatings;
        private Boolean supportsSeriesInfo;
        private Boolean supportsEnhancedMetadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimit {
        private Integer requestsPerMinute;
        private Integer requestsPerDay;
    }
}
