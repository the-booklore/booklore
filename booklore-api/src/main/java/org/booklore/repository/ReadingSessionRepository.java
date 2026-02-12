package org.booklore.repository;

import org.booklore.model.dto.*;
import org.booklore.model.dto.CompletionRaceSessionDto;
import org.booklore.model.dto.PageTurnerSessionDto;

import org.booklore.model.entity.ReadingSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReadingSessionRepository extends JpaRepository<ReadingSessionEntity, Long> {

    @Query("""
            SELECT CAST(rs.startTime AS LocalDate) as date, COUNT(rs) as count
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND YEAR(rs.startTime) = :year
            GROUP BY CAST(rs.startTime AS LocalDate)
            ORDER BY date
            """)
    List<ReadingSessionCountDto> findSessionCountsByUserAndYear(@Param("userId") Long userId, @Param("year") int year);

    @Query("""
            SELECT CAST(rs.startTime AS LocalDate) as date, COUNT(rs) as count
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND YEAR(rs.startTime) = :year
            AND MONTH(rs.startTime) = :month
            GROUP BY CAST(rs.startTime AS LocalDate)
            ORDER BY date
            """)
    List<ReadingSessionCountDto> findSessionCountsByUserAndYearAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month);

        @Query("""
                        SELECT
                                b.id as bookId,
                                COALESCE(b.metadata.title,
                                        (SELECT bf.fileName FROM BookFileEntity bf WHERE bf.book.id = b.id ORDER BY bf.id ASC LIMIT 1),
                                        'Unknown Book') as bookTitle,
                                rs.bookType as bookFileType,
                                rs.startTime as startDate,
                                rs.endTime as endDate,
                                1L as totalSessions,
                                rs.durationSeconds as totalDurationSeconds
                        FROM ReadingSessionEntity rs
                        JOIN rs.book b
                        WHERE rs.user.id = :userId
                        AND rs.startTime >= :startOfWeek AND rs.startTime < :endOfWeek
                        ORDER BY rs.startTime
                        """)
    List<ReadingSessionTimelineDto> findSessionTimelineByUserAndWeek(
            @Param("userId") Long userId,
            @Param("startOfWeek") Instant startOfWeek,
            @Param("endOfWeek") Instant endOfWeek);

    @Query("""
            SELECT
                CAST(rs.createdAt AS LocalDate) as date,
                AVG(rs.progressDelta / (rs.durationSeconds / 60.0)) as avgProgressPerMinute,
                COUNT(rs) as totalSessions
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND rs.durationSeconds > 0
            AND rs.progressDelta > 0
            AND YEAR(rs.createdAt) = :year
            GROUP BY CAST(rs.createdAt AS LocalDate)
            ORDER BY date
            """)
    List<ReadingSpeedDto> findReadingSpeedByUserAndYear(@Param("userId") Long userId, @Param("year") int year);

    @Query("""
            SELECT
                HOUR(rs.startTime) as hourOfDay,
                COUNT(rs) as sessionCount,
                SUM(rs.durationSeconds) as totalDurationSeconds
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND (:year IS NULL OR YEAR(rs.startTime) = :year)
            AND (:month IS NULL OR MONTH(rs.startTime) = :month)
            GROUP BY HOUR(rs.startTime)
            ORDER BY hourOfDay
            """)
    List<PeakReadingHourDto> findPeakReadingHoursByUser(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month);

    @Query("""
            SELECT
                DAYOFWEEK(rs.startTime) as dayOfWeek,
                COUNT(rs) as sessionCount,
                COALESCE(SUM(rs.durationSeconds), 0) as totalDurationSeconds
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND (:year IS NULL OR YEAR(rs.startTime) = :year)
            AND (:month IS NULL OR MONTH(rs.startTime) = :month)
            GROUP BY DAYOFWEEK(rs.startTime)
            ORDER BY dayOfWeek
            """)
    List<FavoriteReadingDayDto> findFavoriteReadingDaysByUser(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month);

    @Query("""
            SELECT
                c.name as genre,
                COUNT(DISTINCT b.id) as bookCount,
                COUNT(rs) as totalSessions,
                SUM(rs.durationSeconds) as totalDurationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            JOIN b.metadata.categories c
            WHERE rs.user.id = :userId
            GROUP BY c.name
            ORDER BY totalSessions DESC
            """)
    List<GenreStatisticsDto> findGenreStatisticsByUser(@Param("userId") Long userId);

    @Query("""
            SELECT rs
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND rs.book.id = :bookId
            ORDER BY rs.startTime DESC
            """)
    Page<ReadingSessionEntity> findByUserIdAndBookId(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId,
            Pageable pageable);

    @Query("""
            SELECT
                b.id as bookId,
                COALESCE(b.metadata.title, 'Unknown Book') as bookTitle,
                b.metadata.pageCount as pageCount,
                ubp.personalRating as personalRating,
                COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime) as dateFinished,
                rs.startTime as startTime,
                rs.endTime as endTime,
                rs.durationSeconds as durationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            JOIN UserBookProgressEntity ubp ON ubp.book.id = b.id AND ubp.user.id = rs.user.id
            WHERE rs.user.id = :userId
            AND ubp.readStatus = org.booklore.model.enums.ReadStatus.READ
            AND COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime) IS NOT NULL
            ORDER BY b.id, rs.startTime ASC
            """)
    List<PageTurnerSessionDto> findPageTurnerSessionsByUser(@Param("userId") Long userId);

    @Query("""
            SELECT
                b.id as bookId,
                COALESCE(b.metadata.title, 'Unknown Book') as bookTitle,
                rs.startTime as sessionDate,
                rs.endProgress as endProgress
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            JOIN UserBookProgressEntity ubp ON ubp.book.id = b.id AND ubp.user.id = rs.user.id
            WHERE rs.user.id = :userId
            AND ubp.readStatus = org.booklore.model.enums.ReadStatus.READ
            AND YEAR(COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime)) = :year
            AND rs.endProgress IS NOT NULL
            ORDER BY b.id, rs.startTime ASC
            """)
    List<CompletionRaceSessionDto> findCompletionRaceSessionsByUserAndYear(
            @Param("userId") Long userId,
            @Param("year") int year);

    @Query("""
            SELECT CAST(rs.startTime AS LocalDate) as date, COUNT(rs) as count
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            GROUP BY CAST(rs.startTime AS LocalDate)
            ORDER BY date
            """)
    List<ReadingSessionCountDto> findAllSessionCountsByUser(@Param("userId") Long userId);
}
