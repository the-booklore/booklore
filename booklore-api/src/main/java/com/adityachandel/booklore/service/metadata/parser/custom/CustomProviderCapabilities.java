package com.adityachandel.booklore.service.metadata.parser.custom;

import lombok.Data;

@Data
public class CustomProviderCapabilities {
    private String providerId;
    private String providerName;
    private String version;
    private Capabilities capabilities;
    private RateLimit rateLimit;

    @Data
    public static class Capabilities {
        private boolean supportsMetadata;
        private boolean supportsCovers;
        private boolean supportsIsbnSearch;
        private boolean supportsTitleAuthorSearch;
        private boolean supportsRatings;
        private boolean supportsSeriesInfo;
        private boolean supportsEnhancedMetadata;
    }

    @Data
    public static class RateLimit {
        private Integer requestsPerMinute;
        private Integer requestsPerDay;
    }
}
