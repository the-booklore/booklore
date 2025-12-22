package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.model.enums.BookFileType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "reading_sessions")
public class ReadingSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    @Enumerated(EnumType.STRING)
    @Column(name = "book_type", nullable = false)
    private BookFileType bookType;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "start_progress", nullable = false)
    private Float startProgress;

    @Column(name = "end_progress", nullable = false)
    private Float endProgress;

    @Column(name = "progress_delta", nullable = false)
    private Float progressDelta;

    @Column(name = "start_location", nullable = false, length = 500)
    private String startLocation;

    @Column(name = "end_location", nullable = false, length = 500)
    private String endLocation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}

