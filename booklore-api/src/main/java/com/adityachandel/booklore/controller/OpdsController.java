package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.BookService;
import com.adityachandel.booklore.service.opds.OpdsFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OPDS", description = "Endpoints for OPDS catalog feeds, book downloads, covers, and search description")
@Slf4j
@RestController
@RequestMapping("/api/v1/opds")
@RequiredArgsConstructor
public class OpdsController {

    private static final String OPDS_CATALOG_MEDIA_TYPE = "application/atom+xml;profile=opds-catalog;kind=navigation;charset=utf-8";
    private static final String OPDS_ACQUISITION_MEDIA_TYPE = "application/atom+xml;profile=opds-catalog;kind=acquisition;charset=utf-8";

    private final OpdsFeedService opdsFeedService;
    private final BookService bookService;

    @Operation(summary = "Download book file", description = "Download the book file by its ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Book file downloaded successfully"),
        @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/{bookId}/download")
    public ResponseEntity<Resource> downloadBook(
            @Parameter(description = "ID of the book to download") @PathVariable("bookId") Long bookId) {
        return bookService.downloadBook(bookId);
    }

    @Operation(summary = "Get book cover image", description = "Retrieve the cover image for a book by its ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cover image returned successfully"),
        @ApiResponse(responseCode = "404", description = "Book or cover not found")
    })
    @GetMapping("/{bookId}/cover")
    public ResponseEntity<Resource> getBookCover(
            @Parameter(description = "ID of the book") @PathVariable long bookId) {
        Resource coverImage = bookService.getBookThumbnail(bookId);
        String contentType = "image/jpeg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + coverImage.getFilename() + "\"")
                .body(coverImage);
    }

    @Operation(summary = "Get OPDS root catalog", description = "Retrieve the OPDS root navigation feed.")
    @ApiResponse(responseCode = "200", description = "Root OPDS catalog returned successfully")
    @GetMapping(produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getRootCatalog(
            @Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateRootNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get OPDS libraries navigation", description = "Retrieve the OPDS libraries navigation feed.")
    @ApiResponse(responseCode = "200", description = "Libraries navigation feed returned successfully")
    @GetMapping(value = "/libraries", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getLibrariesNavigation(
            @Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateLibrariesNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get OPDS shelves navigation", description = "Retrieve the OPDS shelves navigation feed.")
    @ApiResponse(responseCode = "200", description = "Shelves navigation feed returned successfully")
    @GetMapping(value = "/shelves", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getShelvesNavigation(
            @Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateShelvesNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get OPDS catalog feed", description = "Retrieve the OPDS acquisition catalog feed.")
    @ApiResponse(responseCode = "200", description = "Catalog feed returned successfully")
    @GetMapping(value = "/catalog", produces = OPDS_ACQUISITION_MEDIA_TYPE)
    public ResponseEntity<String> getCatalog(
            @Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateCatalogFeed(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_ACQUISITION_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get recent books feed", description = "Retrieve the OPDS feed for recently added books.")
    @ApiResponse(responseCode = "200", description = "Recent books feed returned successfully")
    @GetMapping(value = "/recent", produces = OPDS_ACQUISITION_MEDIA_TYPE)
    public ResponseEntity<String> getRecentBooks(
            @Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateRecentFeed(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_ACQUISITION_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get surprise feed", description = "Retrieve the OPDS feed for surprise/random books.")
    @ApiResponse(responseCode = "200", description = "Surprise feed returned successfully")
    @GetMapping(value = "/surprise", produces = OPDS_ACQUISITION_MEDIA_TYPE)
    public ResponseEntity<String> getSurpriseFeed(
            @Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateSurpriseFeed(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_ACQUISITION_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get OPDS search description", description = "Retrieve the OpenSearch description document for OPDS search.")
    @ApiResponse(responseCode = "200", description = "OpenSearch description returned successfully")
    @GetMapping(value = "/search.opds", produces = {
            "application/opensearchdescription+xml",
            "application/atom+xml",
            "application/xml",
            "text/xml"
    })
    public ResponseEntity<String> getSearchDescription() {
        String searchDoc = opdsFeedService.getOpenSearchDescription();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/opensearchdescription+xml;charset=utf-8"))
                .body(searchDoc);
    }
}
