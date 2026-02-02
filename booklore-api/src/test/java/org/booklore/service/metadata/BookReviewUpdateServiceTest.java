package org.booklore.service.metadata;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BookReview;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.BookReviewEntity;
import org.booklore.model.enums.MetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookReviewUpdateServiceTest {

    private BookReviewUpdateService service;

    @BeforeEach
    void setUp() {
        service = new BookReviewUpdateService();
    }

    private BookReview buildDto(MetadataProvider provider, Instant date) {
        return BookReview.builder()
            .metadataProvider(provider)
            .reviewerName("alice")
            .title("Great Book")
            .rating(4.5f)
            .date(date)
            .body("insightful")
            .spoiler(false)
            .followersCount(42)
            .textReviewsCount(3)
            .country("US")
            .build();
    }

    @Test
    void lockedEntityShouldRemainUnchanged() {
        BookMetadataEntity entity = new BookMetadataEntity();
        entity.setReviewsLocked(true);

        BookReviewEntity existing = BookReviewEntity.builder()
            .metadataProvider(MetadataProvider.Amazon)
            .build();
        entity.getReviews().add(existing);

        List<BookReview> incoming = Collections.singletonList(buildDto(MetadataProvider.Amazon, Instant.now()));

        service.updateBookReviews(BookMetadata.builder()
                .bookReviews(incoming)
                .build(), entity, new MetadataClearFlags(), true);

        assertEquals(1, entity.getReviews().size());
        assertTrue(entity.getReviews().stream().anyMatch(r -> r.getMetadataProvider() == MetadataProvider.Amazon));
    }

    @Test
    void clearFlagRemovesAllReviews() {
        BookMetadataEntity entity = new BookMetadataEntity();
        entity.setReviewsLocked(false);

        entity.getReviews().add(BookReviewEntity.builder()
            .metadataProvider(MetadataProvider.GoodReads)
            .build()
        );

        MetadataClearFlags flags = new MetadataClearFlags();
        flags.setReviews(true);

        service.updateBookReviews(BookMetadata.builder()
                .bookReviews(Collections.singletonList(
                    buildDto(MetadataProvider.GoodReads, Instant.now())
                ))
                .build(), entity, flags, true);

        assertTrue(entity.getReviews().isEmpty());
    }

    @Test
    void nullOrEmptyReviewsDoNothing() {
        BookMetadataEntity entity = new BookMetadataEntity();
        entity.setReviewsLocked(false);

        entity.getReviews().add(BookReviewEntity.builder()
            .metadataProvider(MetadataProvider.Google)
            .build()
        );

        service.updateBookReviews(BookMetadata.builder().build(), entity, new MetadataClearFlags(), true);
        assertEquals(1, entity.getReviews().size());

        service.updateBookReviews(BookMetadata.builder()
                .bookReviews(Collections.emptyList())
                .build(), entity, new MetadataClearFlags(), true);
        assertEquals(1, entity.getReviews().size());
    }

    @Test
    void mergeWithExistingAddsReviews() {
        BookMetadataEntity entity = new BookMetadataEntity();
        entity.setReviewsLocked(false);

        BookReviewEntity existing = BookReviewEntity.builder()
            .metadataProvider(MetadataProvider.Google)
            .build();
        entity.getReviews().add(existing);

        List<BookReview> incoming = Arrays.asList(
            buildDto(MetadataProvider.Hardcover, Instant.now()),
            buildDto(MetadataProvider.Comicvine, Instant.now())
        );

        service.updateBookReviews(BookMetadata.builder()
                .bookReviews(incoming)
                .build(), entity, new MetadataClearFlags(), true);

        Set<MetadataProvider> providers = entity.getReviews().stream()
            .map(BookReviewEntity::getMetadataProvider)
            .collect(Collectors.toSet());

        assertEquals(3, providers.size());
        assertTrue(providers.containsAll(Arrays.asList(
            MetadataProvider.Google,
            MetadataProvider.Hardcover,
            MetadataProvider.Comicvine
        )));
    }

    @Test
    void replaceClearsAndAddsOnlyIncoming() {
        BookMetadataEntity entity = new BookMetadataEntity();
        entity.setReviewsLocked(false);

        entity.getReviews().add(BookReviewEntity.builder()
            .metadataProvider(MetadataProvider.Amazon)
            .build()
        );

        List<BookReview> incoming = Collections.singletonList(
            buildDto(MetadataProvider.GoodReads, Instant.now())
        );

        service.updateBookReviews(BookMetadata.builder()
                .bookReviews(incoming)
                .build(), entity, new MetadataClearFlags(), false);

        assertEquals(1, entity.getReviews().size());
        assertTrue(entity.getReviews().stream()
            .allMatch(r -> r.getMetadataProvider() == MetadataProvider.GoodReads));
    }

    @Test
    void limitsReviewsPerProviderToFiveNewest() {
        BookMetadataEntity entity = new BookMetadataEntity();
        entity.setReviewsLocked(false);

        List<BookReview> incoming = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 6; i++) {
            incoming.add(buildDto(
                MetadataProvider.Amazon,
                Instant.ofEpochMilli(now - (i * 1000L))
            ));
        }

        service.updateBookReviews(BookMetadata.builder()
                .bookReviews(incoming)
                .build(), entity, new MetadataClearFlags(), false);

        assertEquals(5, entity.getReviews().size());

        List<Instant> keptDates = entity.getReviews().stream()
            .map(BookReviewEntity::getDate)
            .sorted(Comparator.reverseOrder())
            .toList();

        assertFalse(keptDates.contains(
            Instant.ofEpochMilli(now - (5 * 1000L))
        ));
    }
}
