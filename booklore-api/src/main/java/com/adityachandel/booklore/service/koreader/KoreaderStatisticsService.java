package com.adityachandel.booklore.service.koreader;

import com.adityachandel.booklore.config.CacheConfig;
import com.adityachandel.booklore.model.dto.koreader.*;
import com.adityachandel.booklore.model.entity.ReadingSessionEntity;
import com.adityachandel.booklore.repository.ReadingSessionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@AllArgsConstructor
@Service
public class KoreaderStatisticsService {

    private final ReadingSessionRepository sessionRepository;

    /**
     * Get reading statistics summary for a user (KOReader sessions only)
     *
     * @param userId The user ID
     * @return Summary of reading statistics
     */
    @Cacheable(value = CacheConfig.KOREADER_STATS_SUMMARY, key = "#userId")
    public ReadingStatsSummary getReadingStatsSummary(Long userId) {
        log.debug("Calculating reading statistics for userId={}", userId);

        // Get all KOReader sessions for the user (filtered at database level with eager fetching)
        List<ReadingSessionEntity> sessions = sessionRepository.findKoreaderSessionsByUserId(userId);

        if (sessions.isEmpty()) {
            return ReadingStatsSummary.builder()
                    .totalReadingTimeSeconds(0)
                    .totalPagesRead(0)
                    .longestDaySeconds(0)
                    .longestDayDate(null)
                    .mostPagesInDay(0)
                    .mostPagesDate(null)
                    .build();
        }

        // Calculate total reading time
        long totalReadingTime = sessions.stream()
                .mapToLong(ReadingSessionEntity::getDurationSeconds)
                .sum();

        // Total pages read = count of sessions (each session represents reading one page)
        long totalPages = sessions.size();

        // Group sessions by day (using UTC to match KOReader's storage format)
        Map<LocalDate, List<ReadingSessionEntity>> sessionsByDay = sessions.stream()
                .collect(Collectors.groupingBy(session ->
                        session.getStartTime().atZone(ZoneId.of("UTC")).toLocalDate()
                ));

        // Find longest reading day (by total duration)
        Map.Entry<LocalDate, Long> longestDay = sessionsByDay.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .mapToLong(ReadingSessionEntity::getDurationSeconds)
                                .sum()
                ))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        // Find day with most pages read (count of sessions per day)
        Map.Entry<LocalDate, Long> mostPagesDay = sessionsByDay.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> (long) entry.getValue().size()
                ))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        return ReadingStatsSummary.builder()
                .totalReadingTimeSeconds(totalReadingTime)
                .totalPagesRead(totalPages)
                .longestDaySeconds(longestDay != null ? longestDay.getValue() : 0)
                .longestDayDate(longestDay != null ? longestDay.getKey() : null)
                .mostPagesInDay(mostPagesDay != null ? mostPagesDay.getValue() : 0)
                .mostPagesDate(mostPagesDay != null ? mostPagesDay.getKey() : null)
                .build();
    }

    /**
     * Get daily reading statistics for heatmap display.
     * Only returns days that have reading activity (no empty days).
     *
     * @param userId The user ID
     * @return List of daily reading stats, sorted by date
     */
    @Cacheable(value = CacheConfig.KOREADER_STATS_DAILY, key = "#userId")
    public List<DailyReadingStats> getDailyReadingStats(Long userId) {
        log.debug("Getting daily reading stats for userId={}", userId);

        // Get all KOReader sessions for the user (filtered at database level)
        List<ReadingSessionEntity> sessions = sessionRepository.findKoreaderSessionsByUserId(userId);

        if (sessions.isEmpty()) {
            return List.of();
        }

        // Group sessions by day (using UTC)
        Map<LocalDate, List<ReadingSessionEntity>> sessionsByDay = sessions.stream()
                .collect(Collectors.groupingBy(session ->
                        session.getStartTime().atZone(ZoneId.of("UTC")).toLocalDate()
                ));

        // Convert to DailyReadingStats, only including days with activity
        return sessionsByDay.entrySet().stream()
                .map(entry -> DailyReadingStats.builder()
                        .date(entry.getKey())
                        .durationSeconds(entry.getValue().stream()
                                .mapToLong(ReadingSessionEntity::getDurationSeconds)
                                .sum())
                        .pagesRead(entry.getValue().size())
                        .build())
                .sorted(Comparator.comparing(DailyReadingStats::getDate))
                .toList();
    }

    /**
     * Get reading statistics grouped by day of week.
     *
     * @param userId The user ID
     * @return List of stats for each day of week (Monday=0 through Sunday=6)
     */
    @Cacheable(value = CacheConfig.KOREADER_STATS_DAY_OF_WEEK, key = "#userId")
    public List<DayOfWeekStats> getDayOfWeekStats(Long userId) {
        log.debug("Getting day of week stats for userId={}", userId);

        List<ReadingSessionEntity> sessions = sessionRepository.findKoreaderSessionsByUserId(userId);

        if (sessions.isEmpty()) {
            return getEmptyDayOfWeekStats();
        }

        // Day names starting from Monday
        String[] dayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

        // Group sessions by day of week (Monday = 0, Sunday = 6)
        Map<Integer, List<ReadingSessionEntity>> sessionsByDayOfWeek = sessions.stream()
                .collect(Collectors.groupingBy(session -> {
                    DayOfWeek dow = session.getStartTime().atZone(ZoneId.of("UTC")).getDayOfWeek();
                    return dow.getValue() - 1; // Convert to 0-based (Monday=0)
                }));

        // Build stats for each day of week
        return IntStream.range(0, 7)
                .mapToObj(dayIndex -> {
                    List<ReadingSessionEntity> daySessions = sessionsByDayOfWeek.getOrDefault(dayIndex, List.of());
                    return DayOfWeekStats.builder()
                            .dayName(dayNames[dayIndex])
                            .dayIndex(dayIndex)
                            .totalDurationSeconds(daySessions.stream()
                                    .mapToLong(ReadingSessionEntity::getDurationSeconds)
                                    .sum())
                            .totalPagesRead(daySessions.size())
                            .sessionCount(daySessions.size())
                            .build();
                })
                .toList();
    }

    private List<DayOfWeekStats> getEmptyDayOfWeekStats() {
        String[] dayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        return IntStream.range(0, 7)
                .mapToObj(i -> DayOfWeekStats.builder()
                        .dayName(dayNames[i])
                        .dayIndex(i)
                        .totalDurationSeconds(0)
                        .totalPagesRead(0)
                        .sessionCount(0)
                        .build())
                .toList();
    }

    /**
     * Get calendar data for a specific month, including book reading spans.
     *
     * @param userId The user ID
     * @param year   The year
     * @param month  The month (1-12)
     * @return Calendar month data with daily stats and book spans
     */
    @Cacheable(value = CacheConfig.KOREADER_STATS_CALENDAR, key = "#userId + '-' + #year + '-' + #month")
    public CalendarMonthData getCalendarMonthData(Long userId, int year, int month) {
        log.debug("Getting calendar data for userId={}, year={}, month={}", userId, year, month);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // Convert to Instant for database query (use start of day UTC for start, end of day UTC for end)
        Instant startInstant = startDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().minusNanos(1);

        // Get KOReader sessions for the user in this month (filtered at database level with eager fetching)
        List<ReadingSessionEntity> sessions = sessionRepository.findKoreaderSessionsByUserIdAndDateRange(
                userId, startInstant, endInstant);

        // Group sessions by day
        Map<LocalDate, List<ReadingSessionEntity>> sessionsByDay = sessions.stream()
                .collect(Collectors.groupingBy(session ->
                        session.getStartTime().atZone(ZoneId.of("UTC")).toLocalDate()
                ));

        // Build daily data
        List<CalendarDayData> days = sessionsByDay.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<ReadingSessionEntity> daySessions = entry.getValue();

                    // Group by book
                    Map<Long, List<ReadingSessionEntity>> sessionsByBook = daySessions.stream()
                            .collect(Collectors.groupingBy(s -> s.getBook().getId()));

                    List<BookDayReading> books = sessionsByBook.entrySet().stream()
                            .map(bookEntry -> {
                                ReadingSessionEntity firstSession = bookEntry.getValue().get(0);
                                String title = firstSession.getBook().getMetadata() != null
                                        ? firstSession.getBook().getMetadata().getTitle()
                                        : firstSession.getBook().getFileName();
                                return BookDayReading.builder()
                                        .bookId(bookEntry.getKey())
                                        .title(title)
                                        .durationSeconds(bookEntry.getValue().stream()
                                                .mapToLong(ReadingSessionEntity::getDurationSeconds)
                                                .sum())
                                        .pagesRead(bookEntry.getValue().size())
                                        .build();
                            })
                            .sorted(Comparator.comparing(BookDayReading::getDurationSeconds).reversed())
                            .toList();

                    return CalendarDayData.builder()
                            .date(date)
                            .totalDurationSeconds(daySessions.stream()
                                    .mapToLong(ReadingSessionEntity::getDurationSeconds)
                                    .sum())
                            .totalPagesRead(daySessions.size())
                            .books(books)
                            .build();
                })
                .sorted(Comparator.comparing(CalendarDayData::getDate))
                .toList();

        // Build book spans (for the bar visualization)
        List<BookReadingSpan> bookSpans = buildBookSpans(sessions, startDate, endDate);

        return CalendarMonthData.builder()
                .year(year)
                .month(month)
                .days(days)
                .bookSpans(bookSpans)
                .build();
    }

    /**
     * Build book reading spans for calendar visualization.
     * Each span represents a book's reading period within the month, with row assignment for overlapping books.
     */
    private List<BookReadingSpan> buildBookSpans(List<ReadingSessionEntity> sessions, LocalDate monthStart, LocalDate monthEnd) {
        // Group sessions by book
        Map<Long, List<ReadingSessionEntity>> sessionsByBook = sessions.stream()
                .collect(Collectors.groupingBy(s -> s.getBook().getId()));

        // Create spans for each book
        List<BookReadingSpan> spans = new ArrayList<>();

        for (Map.Entry<Long, List<ReadingSessionEntity>> entry : sessionsByBook.entrySet()) {
            List<ReadingSessionEntity> bookSessions = entry.getValue();
            if (bookSessions.isEmpty()) continue;

            ReadingSessionEntity firstSession = bookSessions.get(0);
            String title = firstSession.getBook().getMetadata() != null
                    ? firstSession.getBook().getMetadata().getTitle()
                    : firstSession.getBook().getFileName();

            // Find the date range for this book within the month
            List<LocalDate> dates = bookSessions.stream()
                    .map(s -> s.getStartTime().atZone(ZoneId.of("UTC")).toLocalDate())
                    .distinct()
                    .sorted()
                    .toList();

            LocalDate bookStartDate = dates.get(0);
            LocalDate bookEndDate = dates.get(dates.size() - 1);

            // Clamp to month boundaries
            bookStartDate = bookStartDate.isBefore(monthStart) ? monthStart : bookStartDate;
            bookEndDate = bookEndDate.isAfter(monthEnd) ? monthEnd : bookEndDate;

            long totalDuration = bookSessions.stream()
                    .mapToLong(ReadingSessionEntity::getDurationSeconds)
                    .sum();

            spans.add(BookReadingSpan.builder()
                    .bookId(entry.getKey())
                    .title(title)
                    .startDate(bookStartDate)
                    .endDate(bookEndDate)
                    .totalDurationSeconds(totalDuration)
                    .totalPagesRead(bookSessions.size())
                    .row(0) // Will be assigned later
                    .build());
        }

        // Sort spans by start date, then by duration (longer spans first)
        spans.sort(Comparator.comparing(BookReadingSpan::getStartDate)
                .thenComparing(Comparator.comparing(BookReadingSpan::getTotalDurationSeconds).reversed()));

        // Assign rows to avoid overlaps (greedy algorithm)
        assignRows(spans);

        return spans;
    }

    /**
     * Assign row numbers to spans to avoid visual overlaps.
     * Uses a greedy algorithm that places each span in the first available row.
     */
    private void assignRows(List<BookReadingSpan> spans) {
        // Track the end date of the last span in each row
        List<LocalDate> rowEndDates = new ArrayList<>();

        for (BookReadingSpan span : spans) {
            int assignedRow = -1;

            // Find the first row where this span fits (doesn't overlap)
            for (int row = 0; row < rowEndDates.size(); row++) {
                if (span.getStartDate().isAfter(rowEndDates.get(row))) {
                    assignedRow = row;
                    rowEndDates.set(row, span.getEndDate());
                    break;
                }
            }

            // If no existing row fits, create a new one
            if (assignedRow == -1) {
                assignedRow = rowEndDates.size();
                rowEndDates.add(span.getEndDate());
            }

            span.setRow(assignedRow);
        }
    }
}
