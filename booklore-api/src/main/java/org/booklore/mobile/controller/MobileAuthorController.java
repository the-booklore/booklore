package org.booklore.mobile.controller;

import org.booklore.mobile.dto.MobileAuthorDetail;
import org.booklore.mobile.dto.MobileAuthorSummary;
import org.booklore.mobile.dto.MobilePageResponse;
import org.booklore.mobile.service.MobileAuthorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/mobile/v1/authors")
@Tag(name = "Mobile Authors", description = "Mobile-optimized endpoints for browsing authors")
public class MobileAuthorController {

    private final MobileAuthorService mobileAuthorService;

    @Operation(summary = "Get paginated author list",
            description = "Retrieve a paginated list of authors with optional filtering by library, search text, and photo availability.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authors retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping
    public ResponseEntity<MobilePageResponse<MobileAuthorSummary>> getAuthors(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size (max 50)") @RequestParam(required = false, defaultValue = "30") Integer size,
            @Parameter(description = "Sort field (name, bookCount, recent)") @RequestParam(required = false, defaultValue = "name") String sort,
            @Parameter(description = "Sort direction (asc, desc)") @RequestParam(required = false, defaultValue = "asc") String dir,
            @Parameter(description = "Filter by library ID") @RequestParam(required = false) Long libraryId,
            @Parameter(description = "Search by author name") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by photo availability") @RequestParam(required = false) Boolean hasPhoto) {

        return ResponseEntity.ok(mobileAuthorService.getAuthors(page, size, sort, dir, libraryId, search, hasPhoto));
    }

    @Operation(summary = "Get author details",
            description = "Retrieve full details for a specific author including description and book count.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Author details retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Author not found")
    })
    @GetMapping("/{authorId}")
    public ResponseEntity<MobileAuthorDetail> getAuthorDetail(
            @Parameter(description = "Author ID") @PathVariable Long authorId) {

        return ResponseEntity.ok(mobileAuthorService.getAuthorDetail(authorId));
    }
}
