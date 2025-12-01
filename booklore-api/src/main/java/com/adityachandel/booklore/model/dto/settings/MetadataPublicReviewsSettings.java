package com.adityachandel.booklore.model.dto.settings;

import com.adityachandel.booklore.model.enums.MetadataProvider;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Builder
@Data
public class MetadataPublicReviewsSettings {

    private boolean downloadEnabled;
    private boolean autoDownloadEnabled;
    private Set<ReviewProviderConfig> providers;

    @Builder
    @Data
    public static class ReviewProviderConfig {
        private MetadataProvider provider;
        private boolean enabled;
        private int maxReviews;
    }
}
