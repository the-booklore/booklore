package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.ReadingSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReadingSessionRepository extends JpaRepository<ReadingSessionEntity, Long> {

    /**
     * Find all reading sessions for a user within a date range
     */
    @Query("SELECT rs FROM ReadingSessionEntity rs WHERE rs.user.id = :userId AND rs.startTime BETWEEN :startDate AND :endDate ORDER BY rs.startTime")
    List<ReadingSessionEntity> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Find all reading sessions for a specific book and user
     */
    @Query("SELECT rs FROM ReadingSessionEntity rs WHERE rs.user.id = :userId AND rs.book.id = :bookId ORDER BY rs.startTime")
    List<ReadingSessionEntity> findByUserIdAndBookId(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId
    );

    /**
     * Find all reading sessions for a user across multiple books (for bulk import optimization)
     */
    @Query("SELECT rs FROM ReadingSessionEntity rs WHERE rs.user.id = :userId AND rs.book.id IN :bookIds")
    List<ReadingSessionEntity> findByUserIdAndBookIdIn(
            @Param("userId") Long userId,
            @Param("bookIds") List<Long> bookIds
    );

    /**
     * Check if a session already exists (to prevent duplicates during import)
     */
    boolean existsByUserIdAndBookIdAndPageNumberAndStartTime(
            Long userId,
            Long bookId,
            Integer pageNumber,
            Instant startTime
    );

    /**
     * Count total reading sessions for a user
     */
    long countByUserId(Long userId);

    /**
     * Find all reading sessions for a user
     */
    List<ReadingSessionEntity> findByUserIdOrderByStartTimeDesc(Long userId);

    /**
     * Find all KOReader reading sessions for a user (with eager fetch of book and metadata)
     */
    @Query("SELECT rs FROM ReadingSessionEntity rs " +
           "JOIN FETCH rs.book b " +
           "LEFT JOIN FETCH b.metadata " +
           "WHERE rs.user.id = :userId AND rs.source LIKE 'koreader%' " +
           "ORDER BY rs.startTime DESC")
    List<ReadingSessionEntity> findKoreaderSessionsByUserId(@Param("userId") Long userId);

    /**
     * Find KOReader reading sessions for a user within a date range (with eager fetch)
     */
    @Query("SELECT rs FROM ReadingSessionEntity rs " +
           "JOIN FETCH rs.book b " +
           "LEFT JOIN FETCH b.metadata " +
           "WHERE rs.user.id = :userId " +
           "AND rs.source LIKE 'koreader%' " +
           "AND rs.startTime BETWEEN :startDate AND :endDate " +
           "ORDER BY rs.startTime")
    List<ReadingSessionEntity> findKoreaderSessionsByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}
