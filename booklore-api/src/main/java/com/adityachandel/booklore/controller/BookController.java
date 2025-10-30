package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.annotation.CheckBookAccess;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookRecommendation;
import com.adityachandel.booklore.model.dto.BookViewerSettings;
import com.adityachandel.booklore.model.dto.request.ReadProgressRequest;
import com.adityachandel.booklore.model.dto.request.ReadStatusUpdateRequest;
import com.adityachandel.booklore.model.dto.request.ShelvesAssignmentRequest;
import com.adityachandel.booklore.model.dto.response.BookDeletionResponse;
import com.adityachandel.booklore.model.enums.ResetProgressType;
import com.adityachandel.booklore.service.book.BookService;
import com.adityachandel.booklore.service.metadata.BookMetadataService;
import com.adityachandel.booklore.service.recommender.BookRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Tag(name = "Books", description = "Endpoints for managing books, their metadata, progress, and recommendations")
@RequestMapping("/api/v1/books")
@RestController
@AllArgsConstructor
public class BookController {

    private final BookService bookService;
    private final BookRecommendationService bookRecommendationService;
    private final BookMetadataService bookMetadataService;

    @Operation(summary = "Get all books", description = "Retrieve a list of all books. Optionally include descriptions.")
    @ApiResponse(responseCode = "200", description = "List of books returned successfully")
    @GetMapping
    public ResponseEntity<List<Book>> getBooks(
            @Parameter(description = "Include book descriptions in the response")
            @RequestParam(required = false, defaultValue = "false") boolean withDescription) {
        return ResponseEntity.ok(bookService.getBookDTOs(withDescription));
    }

    @Operation(summary = "Get a book by ID", description = "Retrieve details of a specific book by its ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Book details returned successfully"),
        @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/{bookId}")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Book> getBook(
            @Parameter(description = "ID of the book to retrieve") @PathVariable long bookId,
            @Parameter(description = "Include book description in the response") @RequestParam(required = false, defaultValue = "false") boolean withDescription) {
        return ResponseEntity.ok(bookService.getBook(bookId, withDescription));
    }

    @Operation(summary = "Delete books", description = "Delete one or more books by their IDs. Requires admin or delete permission.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Books deleted successfully"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PreAuthorize("@securityUtil.canDeleteBook() or @securityUtil.isAdmin()")
    @DeleteMapping
    public ResponseEntity<BookDeletionResponse> deleteBooks(
            @Parameter(description = "Set of book IDs to delete") @RequestParam Set<Long> ids) {
        return bookService.deleteBooks(ids);
    }

    @Operation(summary = "Get books by IDs", description = "Retrieve multiple books by their IDs. Optionally include descriptions.")
    @ApiResponse(responseCode = "200", description = "Books returned successfully")
    @GetMapping("/batch")
    public ResponseEntity<List<Book>> getBooksByIds(
            @Parameter(description = "Set of book IDs to retrieve") @RequestParam Set<Long> ids,
            @Parameter(description = "Include book descriptions in the response") @RequestParam(required = false, defaultValue = "false") boolean withDescription) {
        return ResponseEntity.ok(bookService.getBooksByIds(ids, withDescription));
    }

    @Operation(summary = "Get ComicInfo metadata", description = "Retrieve ComicInfo metadata for a specific book.")
    @ApiResponse(responseCode = "200", description = "ComicInfo metadata returned successfully")
    @GetMapping("/{bookId}/cbx/metadata/comicinfo")
    public ResponseEntity<?> getComicInfoMetadata(
            @Parameter(description = "ID of the book") @PathVariable long bookId) {
        return ResponseEntity.ok(bookMetadataService.getComicInfoMetadata(bookId));
    }

    @Operation(summary = "Get book content", description = "Retrieve the binary content of a book for reading.")
    @ApiResponse(responseCode = "200", description = "Book content returned successfully")
    @GetMapping("/{bookId}/content")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<ByteArrayResource> getBookContent(
            @Parameter(description = "ID of the book") @PathVariable long bookId) throws IOException {
        return bookService.getBookContent(bookId);
    }

    @Operation(summary = "Download book", description = "Download the book file. Requires download permission or admin.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Book downloaded successfully"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/{bookId}/download")
    @PreAuthorize("@securityUtil.canDownload() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> downloadBook(
            @Parameter(description = "ID of the book to download") @PathVariable("bookId") Long bookId) {
        return bookService.downloadBook(bookId);
    }

    @Operation(summary = "Get viewer settings", description = "Retrieve viewer settings for a specific book.")
    @ApiResponse(responseCode = "200", description = "Viewer settings returned successfully")
    @GetMapping("/{bookId}/viewer-setting")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<BookViewerSettings> getBookViewerSettings(
            @Parameter(description = "ID of the book") @PathVariable long bookId) {
        return ResponseEntity.ok(bookService.getBookViewerSetting(bookId));
    }

    @Operation(summary = "Update viewer settings", description = "Update viewer settings for a specific book.")
    @ApiResponse(responseCode = "204", description = "Viewer settings updated successfully")
    @PutMapping("/{bookId}/viewer-setting")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Void> updateBookViewerSettings(
            @Parameter(description = "Viewer settings to update") @RequestBody BookViewerSettings bookViewerSettings,
            @Parameter(description = "ID of the book") @PathVariable long bookId) {
        bookService.updateBookViewerSetting(bookId, bookViewerSettings);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Assign books to shelves", description = "Assign or unassign books to/from shelves.")
    @ApiResponse(responseCode = "200", description = "Books assigned/unassigned to shelves successfully")
    @PostMapping("/shelves")
    public ResponseEntity<List<Book>> addBookToShelf(
            @Parameter(description = "Shelves assignment request") @RequestBody @Valid ShelvesAssignmentRequest request) {
        return ResponseEntity.ok(bookService.assignShelvesToBooks(request.getBookIds(), request.getShelvesToAssign(), request.getShelvesToUnassign()));
    }

    @Operation(summary = "Update read progress", description = "Update the read progress for a book.")
    @ApiResponse(responseCode = "204", description = "Read progress updated successfully")
    @PostMapping("/progress")
    public ResponseEntity<Void> addBookToProgress(
            @Parameter(description = "Read progress request") @RequestBody @Valid ReadProgressRequest request) {
        bookService.updateReadProgress(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get book recommendations", description = "Get recommended books based on a specific book.")
    @ApiResponse(responseCode = "200", description = "Recommendations returned successfully")
    @GetMapping("/{id}/recommendations")
    @CheckBookAccess(bookIdParam = "id")
    public ResponseEntity<List<BookRecommendation>> getRecommendations(
            @Parameter(description = "ID of the book for recommendations") @PathVariable Long id,
            @Parameter(description = "Maximum number of recommendations to return (max 25)") @RequestParam(defaultValue = "25") @Max(25) @Min(1) int limit) {
        return ResponseEntity.ok(bookRecommendationService.getRecommendations(id, limit));
    }

    @Operation(summary = "Update read status", description = "Update the read status for one or more books.")
    @ApiResponse(responseCode = "200", description = "Read status updated successfully")
    @PutMapping("/read-status")
    public ResponseEntity<List<Book>> updateReadStatus(
            @Parameter(description = "Read status update request") @RequestBody @Valid ReadStatusUpdateRequest request) {
        List<Book> updatedBooks = bookService.updateReadStatus(request.ids(), request.status());
        return ResponseEntity.ok(updatedBooks);
    }

    @Operation(summary = "Reset reading progress", description = "Reset the reading progress for one or more books.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Progress reset successfully"),
        @ApiResponse(responseCode = "400", description = "No book IDs provided")
    })
    @PostMapping("/reset-progress")
    public ResponseEntity<List<Book>> resetProgress(
            @Parameter(description = "List of book IDs to reset progress for") @RequestBody List<Long> bookIds,
            @Parameter(description = "Type of progress reset") @RequestParam ResetProgressType type) {
        if (bookIds == null || bookIds.isEmpty()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("No book IDs provided");
        }
        List<Book> updatedBooks = bookService.resetProgress(bookIds, type);
        return ResponseEntity.ok(updatedBooks);
    }
}
