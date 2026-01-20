package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.annotation.CheckBookAccess;
import com.adityachandel.booklore.model.dto.CoverImage;
import com.adityachandel.booklore.model.dto.request.BulkBookIdsRequest;
import com.adityachandel.booklore.model.dto.request.CoverFetchRequest;
import com.adityachandel.booklore.service.metadata.BookCoverService;
import com.adityachandel.booklore.service.metadata.DuckDuckGoCoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/books")
@AllArgsConstructor
@Tag(name = "Book Metadata", description = "Endpoints for managing book metadata, covers, and metadata operations")
public class BookCoverController {

    private final BookCoverService bookCoverService;
    private final DuckDuckGoCoverService duckDuckGoCoverService;

    @Operation(summary = "Upload cover image from file", description = "Upload a cover image for a book from a file. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Cover image uploaded successfully")
    @PostMapping("/{bookId}/metadata/cover/upload")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void uploadCoverFromFile(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Cover image file") @RequestParam("file") MultipartFile file) {
        bookCoverService.updateCoverFromFile(bookId, file);
    }

    @Operation(summary = "Upload cover image from URL", description = "Upload a cover image for a book from a URL. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Cover image uploaded successfully")
    @PostMapping("/{bookId}/metadata/cover/from-url")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void uploadCoverFromUrl(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "URL body") @RequestBody Map<String, String> body) {
        bookCoverService.updateCoverFromUrl(bookId, body.get("url"));
    }

    @Operation(summary = "Regenerate all covers", description = "Regenerate covers for all books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Covers regenerated successfully")
    @PostMapping("/regenerate-covers")
    @PreAuthorize("@securityUtil.canBulkRegenerateCover() or @securityUtil.isAdmin()")
    public void regenerateCovers() {
        bookCoverService.regenerateCovers();
    }

    @Operation(summary = "Regenerate cover for a book", description = "Regenerate cover for a specific book. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Cover regenerated successfully")
    @PostMapping("/{bookId}/regenerate-cover")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void regenerateCovers(@Parameter(description = "ID of the book") @PathVariable Long bookId) {
        bookCoverService.regenerateCover(bookId);
    }

    @Operation(summary = "Generate custom cover for a book", description = "Generate a custom cover for a specific book based on its metadata. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Custom cover generated successfully")
    @PostMapping("/{bookId}/generate-custom-cover")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void generateCustomCover(@Parameter(description = "ID of the book") @PathVariable Long bookId) {
        bookCoverService.generateCustomCover(bookId);
    }

    @Operation(summary = "Regenerate covers for selected books", description = "Regenerate covers for a list of books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Cover regeneration started successfully")
    @PostMapping("/bulk-regenerate-covers")
    @PreAuthorize("@securityUtil.canBulkRegenerateCover() or @securityUtil.isAdmin()")
    public void regenerateCoversForBooks(@Parameter(description = "List of book IDs") @Validated @RequestBody BulkBookIdsRequest request) {
        bookCoverService.regenerateCoversForBooks(request.getBookIds());
    }

    @Operation(summary = "Generate custom covers for selected books", description = "Generate custom covers for a list of books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Custom cover generation started successfully")
    @PostMapping("/bulk-generate-custom-covers")
    @PreAuthorize("@securityUtil.canBulkRegenerateCover() or @securityUtil.isAdmin()")
    public void generateCustomCoversForBooks(@Parameter(description = "List of book IDs") @Validated @RequestBody BulkBookIdsRequest request) {
        bookCoverService.generateCustomCoversForBooks(request.getBookIds());
    }

    @Operation(summary = "Upload cover image for multiple books", description = "Upload a cover image to apply to multiple books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Cover upload started successfully")
    @PostMapping("/bulk-upload-cover")
    @PreAuthorize("@securityUtil.canBulkEditMetadata() or @securityUtil.isAdmin()")
    public void bulkUploadCover(
            @Parameter(description = "Cover image file") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Comma-separated book IDs") @RequestParam("bookIds") @jakarta.validation.constraints.NotEmpty java.util.Set<Long> bookIds) {
        bookCoverService.updateCoverFromFileForBooks(bookIds, file);
    }

    @Operation(summary = "Get cover images for a book", description = "Fetch cover images for a book.")
    @ApiResponse(responseCode = "200", description = "Cover images returned successfully")
    @PostMapping("/{bookId}/metadata/covers")
    public ResponseEntity<List<CoverImage>> getImages(@Parameter(description = "Cover fetch request") @RequestBody CoverFetchRequest request) {
        return ResponseEntity.ok(duckDuckGoCoverService.getCovers(request));
    }
}