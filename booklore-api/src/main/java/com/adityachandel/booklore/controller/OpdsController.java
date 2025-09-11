package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.BookService;
import com.adityachandel.booklore.service.opds.OpdsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/opds", "/api/v2/opds"})
@RequiredArgsConstructor
public class OpdsController {

    private final OpdsService opdsService;
    private final BookService bookService;

    @GetMapping(produces = {"application/opds+json"})
    public ResponseEntity<String> getRootNavigation(HttpServletRequest request) {
        // Only OPDS 2 navigation is defined for root
        String nav = opdsService.generateOpdsV2Navigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/opds+json;profile=navigation"))
                .body(nav);
    }

    @GetMapping(value = "/libraries", produces = {"application/opds+json"})
    public ResponseEntity<String> getLibrariesNavigation(HttpServletRequest request) {
        String nav = opdsService.generateOpdsV2LibrariesNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/opds+json;profile=navigation"))
                .body(nav);
    }


    @GetMapping(value = "/shelves", produces = {"application/opds+json"})
    public ResponseEntity<String> getShelvesNavigation(HttpServletRequest request) {
        String nav = opdsService.generateOpdsV2ShelvesNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/opds+json;profile=navigation"))
                .body(nav);
    }

    @GetMapping(value = "/catalog", produces = {"application/opds+json", "application/atom+xml;profile=opds-catalog"})
    public ResponseEntity<String> getCatalogFeed(HttpServletRequest request) {
        String feed = opdsService.generateCatalogFeed(request);
        MediaType contentType = selectContentType(request);
        return ResponseEntity.ok()
                .contentType(contentType)
                .body(feed);
    }
   
    @GetMapping(value = "/search", produces = {"application/opds+json", "application/atom+xml;profile=opds-catalog"})
    public ResponseEntity<String> search(HttpServletRequest request) {
        String feed = opdsService.generateSearchResults(request, request.getParameter("q"));
        MediaType contentType = selectContentType(request);
        return ResponseEntity.ok()
                .contentType(contentType)
                .body(feed);
    }

    @GetMapping(value = "/recent", produces = {"application/opds+json"})
    public ResponseEntity<String> recent(HttpServletRequest request) {
        String feed = opdsService.generateRecentFeed(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/opds+json;profile=acquisition"))
                .body(feed);
    }

    @GetMapping(value = "/search.opds", produces = "application/opensearchdescription+xml")
    public ResponseEntity<String> searchDescription(HttpServletRequest request) {
        String feed = opdsService.generateSearchDescription(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/opensearchdescription+xml"))
                .body(feed);
    }

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

    @GetMapping(value = "/publications/{bookId}", produces = "application/opds-publication+json")
    public ResponseEntity<String> getPublication(HttpServletRequest request, @PathVariable long bookId) {
        String publication = opdsService.generateOpdsV2Publication(request, bookId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/opds-publication+json"))
                .body(publication);
    }

    private MediaType selectContentType(HttpServletRequest request) {
        // Force OPDS 2 JSON when using v2-only filters
        if (request.getParameter("shelfId") != null || request.getParameter("libraryId") != null) {
            return MediaType.parseMediaType("application/opds+json;profile=acquisition");
        }
        String accept = request.getHeader("Accept");
        if (accept != null && (accept.contains("application/opds+json") || accept.contains("version=2.0"))) {
            return MediaType.parseMediaType("application/opds+json;profile=acquisition");
        }
        return MediaType.parseMediaType("application/atom+xml;profile=opds-catalog");
    }
}
