package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity representing series completeness information.
 * This table stores pre-calculated data about whether a series is complete or incomplete.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "series_completeness")
public class SeriesCompletenessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "library_id", nullable = false)
    private Long libraryId;

    @Column(name = "series_name", nullable = false, length = 500)
    private String seriesName;

    @Column(name = "series_name_normalized", nullable = false, length = 500)
    private String seriesNameNormalized;

    @Column(name = "book_count", nullable = false)
    private Integer bookCount;

    @Column(name = "min_series_number")
    private Double minSeriesNumber;

    @Column(name = "max_series_number")
    private Double maxSeriesNumber;

    @Column(name = "is_complete", nullable = false)
    private Boolean isComplete;

    @Column(name = "is_incomplete", nullable = false)
    private Boolean isIncomplete;

    @Column(name = "last_calculated_at", nullable = false)
    private Instant lastCalculatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (lastCalculatedAt == null) {
            lastCalculatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
