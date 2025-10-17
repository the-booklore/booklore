package com.adityachandel.booklore.controller.opds;

import com.adityachandel.booklore.service.BookService;
import com.adityachandel.booklore.service.opds.OpdsFeedService;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/opds")
@RequiredArgsConstructor
public class OpdsController {

    private static final String OPDS_CATALOG_MEDIA_TYPE = "application/atom+xml;profile=opds-catalog;kind=navigation;charset=utf-8";
    private static final String OPDS_ACQUISITION_MEDIA_TYPE = "application/atom+xml;profile=opds-catalog;kind=acquisition;charset=utf-8";

    private final OpdsFeedService opdsFeedService;
    private final BookService bookService;

    @GetMapping("/{bookId}/download")
    public ResponseEntity<Resource> downloadBook(@PathVariable("bookId") Long bookId) {
        return bookService.downloadBook(bookId);
    }

    @GetMapping("/{bookId}/cover")
    public ResponseEntity<Resource> getBookCover(@PathVariable long bookId) {
        Resource coverImage = bookService.getBookThumbnail(bookId);
        String contentType = "image/jpeg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + coverImage.getFilename() + "\"")
                .body(coverImage);
    }

    @GetMapping(produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getRootCatalog(HttpServletRequest request) {
        String feed = opdsFeedService.generateRootNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @GetMapping(value = "/libraries", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getLibrariesNavigation(HttpServletRequest request) {
        String feed = opdsFeedService.generateLibrariesNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @GetMapping(value = "/shelves", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getShelvesNavigation(HttpServletRequest request) {
        String feed = opdsFeedService.generateShelvesNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @GetMapping(value = "/catalog", produces = OPDS_ACQUISITION_MEDIA_TYPE)
    public ResponseEntity<String> getCatalog(HttpServletRequest request) {
        String feed = opdsFeedService.generateCatalogFeed(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_ACQUISITION_MEDIA_TYPE))
                .body(feed);
    }

    @GetMapping(value = "/recent", produces = OPDS_ACQUISITION_MEDIA_TYPE)
    public ResponseEntity<String> getRecentBooks(HttpServletRequest request) {
        String feed = opdsFeedService.generateRecentFeed(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_ACQUISITION_MEDIA_TYPE))
                .body(feed);
    }

    @GetMapping(value = "/surprise", produces = OPDS_ACQUISITION_MEDIA_TYPE)
    public ResponseEntity<String> getSurpriseFeed(HttpServletRequest request) {
        String feed = opdsFeedService.generateSurpriseFeed(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_ACQUISITION_MEDIA_TYPE))
                .body(feed);
    }

    @GetMapping(value = "/search.opds", produces = "application/opensearchdescription+xml")
    public ResponseEntity<String> getSearchDescription() {
        String searchDoc = opdsFeedService.getOpenSearchDescription();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/opensearchdescription+xml;charset=utf-8"))
                .body(searchDoc);
    }
}
