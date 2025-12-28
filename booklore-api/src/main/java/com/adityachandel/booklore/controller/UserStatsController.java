package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.response.*;
import com.adityachandel.booklore.service.ReadingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/user-stats")
public class UserStatsController {

    private final ReadingSessionService readingSessionService;

    @Operation(summary = "Get reading session heatmap for a year", description = "Returns daily reading session counts for the authenticated user for a specific year")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Heatmap data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/heatmap")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ReadingSessionHeatmapResponse>> getHeatmapForYear(@RequestParam int year) {
        List<ReadingSessionHeatmapResponse> heatmapData = readingSessionService.getSessionHeatmapForYear(year);
        return ResponseEntity.ok(heatmapData);
    }

    @Operation(summary = "Get reading session timeline for a week", description = "Returns reading sessions grouped by book for calendar timeline view")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timeline data retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid week, month, or year"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/timeline")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ReadingSessionTimelineResponse>> getTimelineForWeek(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam int week) {
        List<ReadingSessionTimelineResponse> timelineData = readingSessionService.getSessionTimelineForWeek(year, month, week);
        return ResponseEntity.ok(timelineData);
    }

    @Operation(summary = "Get reading speed analysis", description = "Returns average reading speed (progress per minute) over time for a specific year")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reading speed data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/speed")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ReadingSpeedResponse>> getReadingSpeedForYear(@RequestParam int year) {
        List<ReadingSpeedResponse> speedData = readingSessionService.getReadingSpeedForYear(year);
        return ResponseEntity.ok(speedData);
    }

    @Operation(summary = "Get peak reading hours", description = "Returns reading activity distribution by hour of day. Can be filtered by year and/or month.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peak reading hours retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/peak-hours")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<PeakReadingHoursResponse>> getPeakReadingHours(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        List<PeakReadingHoursResponse> peakHours = readingSessionService.getPeakReadingHours(year, month);
        return ResponseEntity.ok(peakHours);
    }

    @Operation(summary = "Get favorite reading days", description = "Returns reading activity distribution by day of week. Can be filtered by year and/or month.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Favorite reading days retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/favorite-days")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<FavoriteReadingDaysResponse>> getFavoriteReadingDays(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        List<FavoriteReadingDaysResponse> favoriteDays = readingSessionService.getFavoriteReadingDays(year, month);
        return ResponseEntity.ok(favoriteDays);
    }

    @Operation(summary = "Get genre statistics", description = "Returns reading statistics grouped by book genres/categories")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Genre statistics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/genres")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<GenreStatisticsResponse>> getGenreStatistics() {
        List<GenreStatisticsResponse> genreStats = readingSessionService.getGenreStatistics();
        return ResponseEntity.ok(genreStats);
    }

    @Operation(summary = "Get completion timeline", description = "Returns reading completion statistics over time with status breakdown for a specific year")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completion timeline retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/completion-timeline")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<CompletionTimelineResponse>> getCompletionTimeline(@RequestParam int year) {
        List<CompletionTimelineResponse> timeline = readingSessionService.getCompletionTimeline(year);
        return ResponseEntity.ok(timeline);
    }
}

