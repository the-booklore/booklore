package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "kobo_annotation_sync")
public class KoboAnnotationSyncEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    @Column(name = "book_id", insertable = false, updatable = false)
    private Long bookId;

    @Column(name = "annotation_id", nullable = false)
    private String annotationId;

    @Column(name = "synced_to_hardcover", nullable = false)
    @Builder.Default
    private boolean syncedToHardcover = false;

    @Column(name = "hardcover_journal_id")
    private Integer hardcoverJournalId;

    @Column(name = "highlighted_text", columnDefinition = "TEXT")
    private String highlightedText;

    @Column(name = "note_text", columnDefinition = "TEXT")
    private String noteText;

    @Column(name = "highlight_color", length = 50)
    private String highlightColor;

    @jakarta.persistence.Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
