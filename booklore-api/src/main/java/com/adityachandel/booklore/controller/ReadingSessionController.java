package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.request.ReadingSessionRequest;
import com.adityachandel.booklore.model.dto.response.ReadingSessionHeatmapResponse;
import com.adityachandel.booklore.model.dto.response.ReadingSessionTimelineResponse;
import com.adityachandel.booklore.service.ReadingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/reading-sessions")
public class ReadingSessionController {

    private final ReadingSessionService readingSessionService;

    @Operation(summary = "Record a reading session", description = "Receive telemetry from the reader client and persist or log the session.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Reading session accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid payload")
    })
    @PostMapping
    public ResponseEntity<Void> recordReadingSession(@RequestBody @Valid ReadingSessionRequest request) {
        readingSessionService.recordSession(request);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Get reading session heatmap for a year", description = "Returns daily reading session counts for the authenticated user for a specific year")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Heatmap data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/heatmap/year/{year}")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ReadingSessionHeatmapResponse>> getHeatmapForYear(@PathVariable int year) {
        List<ReadingSessionHeatmapResponse> heatmapData = readingSessionService.getSessionHeatmapForYear(year);
        return ResponseEntity.ok(heatmapData);
    }

    @Operation(summary = "Get reading session timeline for a week", description = "Returns reading sessions grouped by book for calendar timeline view")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timeline data retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid week or year"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/timeline/week/{year}/{week}")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ReadingSessionTimelineResponse>> getTimelineForWeek(
            @PathVariable int year,
            @PathVariable int week) {
        List<ReadingSessionTimelineResponse> timelineData = readingSessionService.getSessionTimelineForWeek(year, week);
        return ResponseEntity.ok(timelineData);
    }
}
