package org.booklore.mobile.controller;

import lombok.AllArgsConstructor;
import org.booklore.mobile.dto.MobileBookSummary;
import org.booklore.mobile.dto.MobilePageResponse;
import org.booklore.mobile.dto.MobileSeriesSummary;
import org.booklore.mobile.service.MobileSeriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/mobile/v1/series")
public class MobileSeriesController {

    private final MobileSeriesService mobileSeriesService;

    @GetMapping
    public ResponseEntity<MobilePageResponse<MobileSeriesSummary>> getSeries(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "recentlyAdded") String sort,
            @RequestParam(required = false, defaultValue = "desc") String dir,
            @RequestParam(required = false) Long libraryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {

        boolean inProgressOnly = "in-progress".equalsIgnoreCase(status);

        MobilePageResponse<MobileSeriesSummary> response = mobileSeriesService.getSeries(
                page, size, sort, dir, libraryId, search, inProgressOnly);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{seriesName}/books")
    public ResponseEntity<MobilePageResponse<MobileBookSummary>> getSeriesBooks(
            @PathVariable String seriesName,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "seriesNumber") String sort,
            @RequestParam(required = false, defaultValue = "asc") String dir,
            @RequestParam(required = false) Long libraryId) {

        MobilePageResponse<MobileBookSummary> response = mobileSeriesService.getSeriesBooks(
                seriesName, page, size, sort, dir, libraryId);

        return ResponseEntity.ok(response);
    }
}
