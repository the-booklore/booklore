package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.koreader.CalendarMonthData;
import com.adityachandel.booklore.model.dto.koreader.DailyReadingStats;
import com.adityachandel.booklore.model.dto.koreader.DayOfWeekStats;
import com.adityachandel.booklore.model.dto.koreader.ReadingStatsSummary;
import com.adityachandel.booklore.service.koreader.KoreaderStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/koreader")
@Tag(name = "KOReader Stats", description = "KOReader reading statistics endpoints for web UI")
public class KoreaderStatsController {

    private final KoreaderStatisticsService statisticsService;

    @Operation(summary = "Get KOReader reading statistics summary", description = "Get summary of reading statistics from KOReader including total time, pages read, and records.")
    @ApiResponse(responseCode = "200", description = "Statistics summary returned successfully")
    @GetMapping("/statistics/summary")
    public ResponseEntity<ReadingStatsSummary> getStatisticsSummary() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof BookLoreUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ReadingStatsSummary summary = statisticsService.getReadingStatsSummary(user.getId());
        return ResponseEntity.ok(summary);
    }

    @Operation(summary = "Get daily reading statistics for heatmap", description = "Get daily reading statistics for displaying a reading activity heatmap. Only includes days with reading activity.")
    @ApiResponse(responseCode = "200", description = "Daily statistics returned successfully")
    @GetMapping("/statistics/daily")
    public ResponseEntity<List<DailyReadingStats>> getDailyStatistics() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof BookLoreUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<DailyReadingStats> dailyStats = statisticsService.getDailyReadingStats(user.getId());
        return ResponseEntity.ok(dailyStats);
    }

    @Operation(summary = "Get calendar month data", description = "Get reading data for a specific month, including book spans for calendar visualization.")
    @ApiResponse(responseCode = "200", description = "Calendar month data returned successfully")
    @GetMapping("/statistics/calendar/{year}/{month}")
    public ResponseEntity<CalendarMonthData> getCalendarMonth(
            @PathVariable int year,
            @PathVariable int month) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof BookLoreUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CalendarMonthData calendarData = statisticsService.getCalendarMonthData(user.getId(), year, month);
        return ResponseEntity.ok(calendarData);
    }

    @Operation(summary = "Get reading statistics by day of week", description = "Get aggregated reading statistics for each day of the week (Monday through Sunday).")
    @ApiResponse(responseCode = "200", description = "Day of week statistics returned successfully")
    @GetMapping("/statistics/day-of-week")
    public ResponseEntity<List<DayOfWeekStats>> getDayOfWeekStatistics() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof BookLoreUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<DayOfWeekStats> stats = statisticsService.getDayOfWeekStats(user.getId());
        return ResponseEntity.ok(stats);
    }
}
