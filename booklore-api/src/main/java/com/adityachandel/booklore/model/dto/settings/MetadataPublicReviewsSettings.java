package com.adityachandel.booklore.model.dto.settings;

import com.adityachandel.booklore.model.enums.MetadataProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataPublicReviewsSettings {

    private boolean downloadEnabled;
    private boolean autoDownloadEnabled;
    private Set<ReviewProviderConfig> providers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewProviderConfig {
        private MetadataProvider provider;
        private boolean enabled;
        private int maxReviews;
    }

}
