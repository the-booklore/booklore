package com.adityachandel.booklore.mobile.controller;

import com.adityachandel.booklore.mobile.dto.*;
import com.adityachandel.booklore.mobile.service.MobileBookService;
import com.adityachandel.booklore.model.enums.ReadStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/mobile/v1/books")
@Tag(name = "Mobile Books", description = "Mobile-optimized endpoints for book operations")
public class MobileBookController {

    private final MobileBookService mobileBookService;

    @Operation(summary = "Get paginated book list",
            description = "Retrieve a paginated list of books with optional filtering by library, shelf, status, and search text.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Books retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping
    public ResponseEntity<MobilePageResponse<MobileBookSummary>> getBooks(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size (max 50)") @RequestParam(required = false, defaultValue = "20") Integer size,
            @Parameter(description = "Sort field (title, addedOn, lastReadTime, seriesName)") @RequestParam(required = false, defaultValue = "addedOn") String sort,
            @Parameter(description = "Sort direction (asc, desc)") @RequestParam(required = false, defaultValue = "desc") String dir,
            @Parameter(description = "Filter by library ID") @RequestParam(required = false) Long libraryId,
            @Parameter(description = "Filter by shelf ID") @RequestParam(required = false) Long shelfId,
            @Parameter(description = "Filter by read status") @RequestParam(required = false) ReadStatus status,
            @Parameter(description = "Search in title, author, series") @RequestParam(required = false) String search) {

        return ResponseEntity.ok(mobileBookService.getBooks(
                page, size, sort, dir, libraryId, shelfId, status, search));
    }

    @Operation(summary = "Get book details",
            description = "Retrieve full details for a specific book.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book details retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/{bookId}")
    public ResponseEntity<MobileBookDetail> getBookDetail(
            @Parameter(description = "Book ID") @PathVariable Long bookId) {

        return ResponseEntity.ok(mobileBookService.getBookDetail(bookId));
    }

    @Operation(summary = "Search books",
            description = "Search books by query text in title, author, and series name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Query parameter required")
    })
    @GetMapping("/search")
    public ResponseEntity<MobilePageResponse<MobileBookSummary>> searchBooks(
            @Parameter(description = "Search query", required = true) @RequestParam String q,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size (max 50)") @RequestParam(required = false, defaultValue = "20") Integer size) {

        return ResponseEntity.ok(mobileBookService.searchBooks(q, page, size));
    }

    @Operation(summary = "Get continue reading list",
            description = "Get books currently in progress, sorted by last read time (most recent first).")
    @ApiResponse(responseCode = "200", description = "Continue reading list retrieved successfully")
    @GetMapping("/continue-reading")
    public ResponseEntity<List<MobileBookSummary>> getContinueReading(
            @Parameter(description = "Maximum number of books to return") @RequestParam(required = false, defaultValue = "10") Integer limit) {

        return ResponseEntity.ok(mobileBookService.getContinueReading(limit));
    }

    @Operation(summary = "Get recently added books",
            description = "Get books added in the last 30 days, sorted by added date (most recent first).")
    @ApiResponse(responseCode = "200", description = "Recently added books retrieved successfully")
    @GetMapping("/recently-added")
    public ResponseEntity<List<MobileBookSummary>> getRecentlyAdded(
            @Parameter(description = "Maximum number of books to return") @RequestParam(required = false, defaultValue = "10") Integer limit) {

        return ResponseEntity.ok(mobileBookService.getRecentlyAdded(limit));
    }

    @Operation(summary = "Update book read status",
            description = "Update the read status for a book.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @PutMapping("/{bookId}/status")
    public ResponseEntity<Void> updateStatus(
            @Parameter(description = "Book ID") @PathVariable Long bookId,
            @Valid @RequestBody UpdateStatusRequest request) {

        mobileBookService.updateReadStatus(bookId, request.getStatus());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Update book personal rating",
            description = "Update the personal rating for a book (1-5).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rating updated successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @PutMapping("/{bookId}/rating")
    public ResponseEntity<Void> updateRating(
            @Parameter(description = "Book ID") @PathVariable Long bookId,
            @Valid @RequestBody UpdateRatingRequest request) {

        mobileBookService.updatePersonalRating(bookId, request.getRating());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get books by magic shelf",
            description = "Retrieve a paginated list of books matching a magic shelf's rules.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Books retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Magic shelf not found")
    })
    @GetMapping("/magic-shelf/{magicShelfId}")
    public ResponseEntity<MobilePageResponse<MobileBookSummary>> getBooksByMagicShelf(
            @Parameter(description = "Magic shelf ID") @PathVariable Long magicShelfId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size (max 50)") @RequestParam(required = false, defaultValue = "20") Integer size) {

        return ResponseEntity.ok(mobileBookService.getBooksByMagicShelf(magicShelfId, page, size));
    }
}
