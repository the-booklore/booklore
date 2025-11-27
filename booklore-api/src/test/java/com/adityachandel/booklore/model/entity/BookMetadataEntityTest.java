package com.adityachandel.booklore.model.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BookMetadataEntityTest {

    @Test
    void builderShouldRespectDefaultValues() {
        BookMetadataEntity metadata = BookMetadataEntity.builder().build();

        assertThat(metadata.getTitleLocked()).isFalse();
        assertThat(metadata.getSubtitleLocked()).isFalse();
        assertThat(metadata.getPublisherLocked()).isFalse();

        assertThat(metadata.getAuthorsLocked()).isFalse();
        assertThat(metadata.getCoverLocked()).isFalse();

        assertThat(metadata.getReviews()).isNotNull().isEmpty();
    }
}