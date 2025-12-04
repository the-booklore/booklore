package com.adityachandel.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "reading_session")
public class ReadingSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @ManyToOne
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "total_pages", nullable = false)
    private Integer totalPages;

    @Column(name = "created_at")
    private Instant createdAt;
}
