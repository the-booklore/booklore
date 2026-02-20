package org.booklore.mobile.controller;

import org.booklore.mobile.dto.MobileFilterOptions;
import org.booklore.mobile.service.MobileBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/mobile/v1")
@Tag(name = "Mobile Filters", description = "Filter options for mobile book browsing")
public class MobileFilterController {

    private final MobileBookService mobileBookService;

    @Operation(summary = "Get available filter options",
            description = "Returns available authors, languages, file types, and read statuses for filtering books.")
    @ApiResponse(responseCode = "200", description = "Filter options retrieved successfully")
    @GetMapping("/filter-options")
    public ResponseEntity<MobileFilterOptions> getFilterOptions() {
        return ResponseEntity.ok(mobileBookService.getFilterOptions());
    }
}
