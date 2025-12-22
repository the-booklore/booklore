package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.dto.ReadingSessionCountDto;
import com.adityachandel.booklore.model.dto.ReadingSessionTimelineDto;
import com.adityachandel.booklore.model.entity.ReadingSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReadingSessionRepository extends JpaRepository<ReadingSessionEntity, Long> {

    @Query("""
            SELECT CAST(rs.createdAt AS LocalDate) as date, COUNT(rs) as count
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND YEAR(rs.createdAt) = :year
            GROUP BY CAST(rs.createdAt AS LocalDate)
            ORDER BY date
            """)
    List<ReadingSessionCountDto> findSessionCountsByUserAndYear(@Param("userId") Long userId, @Param("year") int year);

    @Query("""
            SELECT 
                b.id as bookId,
                b.metadata.title as bookTitle,
                rs.bookType as bookFileType,
                MIN(rs.startTime) as startDate,
                MAX(rs.endTime) as endDate,
                COUNT(rs) as totalSessions,
                SUM(rs.durationSeconds) as totalDurationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            WHERE rs.user.id = :userId
            AND YEAR(rs.startTime) = :year
            AND WEEK(rs.startTime) = :weekOfYear
            GROUP BY b.id, b.metadata.title, rs.bookType
            ORDER BY MIN(rs.startTime)
            """)
    List<ReadingSessionTimelineDto> findSessionTimelineByUserAndWeek(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("weekOfYear") int weekOfYear);
}
